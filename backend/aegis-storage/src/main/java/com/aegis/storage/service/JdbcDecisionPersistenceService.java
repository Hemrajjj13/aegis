package com.aegis.storage.service;

import com.aegis.core.domain.DecisionOutcome;
import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.PaymentEvent;
import com.aegis.core.domain.RuleOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Synchronous decision persistence pipeline enforcing ADR-0002.
 * Writes decision row + processed_events row + outbox row in ONE single transaction with RLS session guard.
 */
@Service
public class JdbcDecisionPersistenceService implements DecisionPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDecisionPersistenceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Sets RLS session variable for the current database transaction.
     */
    @Override
    public void setTenantContext(UUID tenantId) {
        if (tenantId != null) {
            jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantId + "'");
        }
    }

    /**
     * Persists decision, processed_events idempotency record, and outbox event in ONE transaction.
     * If event_id has already been processed with matching payload, returns existing decision.
     */
    @Override
    @Transactional
    public EvaluationResult persistDecisionAtomically(UUID tenantId, PaymentEvent event, EvaluationResult result, String payloadHash) {
        setTenantContext(tenantId);

        // 1. Idempotency Check
        String checkSql = "SELECT decision_id, payload_hash FROM processed_events WHERE tenant_id = ? AND event_id = ?";
        try {
            var existing = jdbcTemplate.queryForObject(checkSql, (rs, rowNum) -> new Object[]{
                    UUID.fromString(rs.getString("decision_id")),
                    rs.getString("payload_hash").trim()
            }, tenantId, event.eventId());

            if (existing != null) {
                UUID existingDecisionId = (UUID) existing[0];
                String existingHash = (String) existing[1];
                if (!existingHash.equals(payloadHash)) {
                    throw new IllegalStateException("Payload hash mismatch for duplicate event_id: " + event.eventId());
                }
                // Return cached decision
                return findDecisionById(tenantId, existingDecisionId)
                        .orElse(result);
            }
        } catch (EmptyResultDataAccessException ignored) {
            // Event has not been processed yet; proceed with write
        }

        Instant now = result.timestamp() != null ? result.timestamp() : Instant.now();
        Timestamp eventTimestamp = Timestamp.from(event.eventTime());
        Timestamp createdTimestamp = Timestamp.from(now);

        String rulesFiredJson = toJson(result.rulesFired());
        String featuresSnapshotJson = toJson(result.featuresSnapshot());

        // 2. Insert into decisions
        String insertDecisionSql = """
            INSERT INTO decisions (
                decision_id, tenant_id, event_id, event_time, sequence_number,
                entity_id_hash, outcome, score, hard_rule_fired, ruleset_id,
                ruleset_version_hash, rules_fired, features_snapshot, degraded,
                degraded_reason, latency_ms, sealed, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, false, ?)
            """;

        String entityIdHash = event.accountIdHash() != null ? event.accountIdHash() : "unknown_entity";

        jdbcTemplate.update(insertDecisionSql,
                result.decisionId(),
                tenantId,
                event.eventId(),
                eventTimestamp,
                event.sequenceNumber(),
                entityIdHash,
                result.outcome().name(),
                result.score(),
                result.hardRuleFired(),
                result.rulesetId(),
                result.rulesetVersionHash(),
                rulesFiredJson,
                featuresSnapshotJson,
                result.degraded(),
                result.degradedReason(),
                result.latencyMs(),
                createdTimestamp
        );

        // 3. Insert into processed_events (deduplication)
        String insertProcessedSql = """
            INSERT INTO processed_events (
                tenant_id, event_id, decision_id, event_time, processed_at, payload_hash
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(insertProcessedSql,
                tenantId,
                event.eventId(),
                result.decisionId(),
                eventTimestamp,
                createdTimestamp,
                payloadHash
        );

        // 4. Insert into outbox (transactional Kafka relay)
        String insertOutboxSql = """
            INSERT INTO outbox (
                tenant_id, aggregate_type, aggregate_id, event_type, payload, headers, created_at
            ) VALUES (?, 'DECISION', ?, 'decision.evaluated', ?::jsonb, ?::jsonb, ?)
            """;

        Map<String, Object> outboxPayload = Map.of(
                "decision_id", result.decisionId().toString(),
                "event_id", event.eventId(),
                "outcome", result.outcome().name(),
                "score", result.score(),
                "ruleset_id", result.rulesetId(),
                "timestamp", now.toString()
        );

        jdbcTemplate.update(insertOutboxSql,
                tenantId,
                result.decisionId().toString(),
                toJson(outboxPayload),
                toJson(Map.of("tenant_id", tenantId.toString())),
                createdTimestamp
        );

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvaluationResult> findDecisionById(UUID tenantId, UUID decisionId) {
        setTenantContext(tenantId);
        String sql = "SELECT * FROM decisions WHERE tenant_id = ? AND decision_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapDecisionRow, tenantId, decisionId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationResult> findAllDecisionsForTenant(UUID tenantId) {
        setTenantContext(tenantId);
        String sql = "SELECT * FROM decisions WHERE tenant_id = ?";
        return jdbcTemplate.query(sql, this::mapDecisionRow, tenantId);
    }

    private EvaluationResult mapDecisionRow(ResultSet rs, int rowNum) throws SQLException {
        UUID decisionId = UUID.fromString(rs.getString("decision_id"));
        String eventId = rs.getString("event_id");
        DecisionOutcome outcome = DecisionOutcome.valueOf(rs.getString("outcome"));
        double score = rs.getDouble("score");
        boolean hardRuleFired = rs.getBoolean("hard_rule_fired");
        String rulesetId = rs.getString("ruleset_id");
        String rulesetVersionHash = rs.getString("ruleset_version_hash");
        boolean degraded = rs.getBoolean("degraded");
        String degradedReason = rs.getString("degraded_reason");
        double latencyMs = rs.getDouble("latency_ms");
        Instant timestamp = rs.getTimestamp("created_at").toInstant();

        List<RuleOutcome> rulesFired = fromJson(rs.getString("rules_fired"), new TypeReference<>() {});
        Map<String, Object> featuresSnapshot = fromJson(rs.getString("features_snapshot"), new TypeReference<>() {});

        return new EvaluationResult(
                decisionId,
                eventId,
                outcome,
                score,
                hardRuleFired,
                rulesetId,
                rulesetVersionHash,
                rulesFired,
                featuresSnapshot,
                degraded,
                degradedReason,
                latencyMs,
                timestamp
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
