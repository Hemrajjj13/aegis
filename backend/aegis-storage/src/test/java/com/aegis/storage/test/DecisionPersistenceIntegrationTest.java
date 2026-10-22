package com.aegis.storage.test;

import com.aegis.core.domain.DecisionOutcome;
import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.PaymentEvent;
import com.aegis.core.domain.RuleOutcome;
import com.aegis.storage.service.DecisionPersistenceService;
import com.aegis.storage.service.JdbcDecisionPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionPersistenceIntegrationTest extends PostgresIntegrationTest {

    private JdbcTemplate jdbcTemplate;
    private DecisionPersistenceService persistenceService;
    private TransactionTemplate transactionTemplate;

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        if (!DockerAvailability.isAvailable()) {
            return;
        }

        var dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(getJdbcUrl());
        dataSource.setUsername(getUsername());
        dataSource.setPassword(getPassword());

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        this.jdbcTemplate = new JdbcTemplate(dataSource);
        var txManager = new DataSourceTransactionManager(dataSource);
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.persistenceService = new JdbcDecisionPersistenceService(jdbcTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("Cross-tenant read returns 0 rows due to Row-Level Security")
    void testCrossTenantReadReturnsZeroRows() {
        PaymentEvent event = createTestEvent("evt_tenant_a_1", TENANT_A, 500000L);
        EvaluationResult result = createTestResult("evt_tenant_a_1", DecisionOutcome.APPROVE, 10.0);

        transactionTemplate.execute(status -> {
            persistenceService.persistDecisionAtomically(TENANT_A, event, result, "hash_tenant_a_1");
            return null;
        });

        // Query under Tenant B's context
        transactionTemplate.execute(status -> {
            persistenceService.setTenantContext(TENANT_B);
            var decisionsForTenantB = jdbcTemplate.queryForList(
                    "SELECT decision_id FROM decisions WHERE event_id = ?",
                    "evt_tenant_a_1"
            );
            assertThat(decisionsForTenantB).isEmpty();
            return null;
        });
    }

    @Test
    @DisplayName("Missing tenant context returns 0 rows due to RLS NULLIF fallback")
    void testMissingTenantContextReturnsZeroRows() {
        PaymentEvent event = createTestEvent("evt_tenant_a_2", TENANT_A, 500000L);
        EvaluationResult result = createTestResult("evt_tenant_a_2", DecisionOutcome.APPROVE, 10.0);

        transactionTemplate.execute(status -> {
            persistenceService.persistDecisionAtomically(TENANT_A, event, result, "hash_tenant_a_2");
            return null;
        });

        // Query with missing/uninitialized tenant context (null/empty current_setting)
        transactionTemplate.execute(status -> {
            jdbcTemplate.execute("SET LOCAL app.current_tenant_id = ''");
            var decisions = jdbcTemplate.queryForList(
                    "SELECT decision_id FROM decisions WHERE event_id = ?",
                    "evt_tenant_a_2"
            );
            assertThat(decisions).isEmpty();
            return null;
        });
    }

    @Test
    @DisplayName("Submitting same event_id twice returns same decision and leaves exactly one row in decisions, processed_events, outbox")
    void testDuplicateEventIdIdempotencyLeavesExactlyOneRow() {
        String eventId = "evt_idempotent_test_1";
        PaymentEvent event = createTestEvent(eventId, TENANT_A, 7500000L);
        EvaluationResult result1 = createTestResult(eventId, DecisionOutcome.REVIEW, 75.0);
        String payloadHash = "sha256_canonical_hash_abc123";

        // First submission
        EvaluationResult persisted1 = transactionTemplate.execute(status ->
                persistenceService.persistDecisionAtomically(TENANT_A, event, result1, payloadHash)
        );
        assertThat(persisted1).isNotNull();

        // Second submission with identical payload hash and different result instance
        EvaluationResult result2 = createTestResult(eventId, DecisionOutcome.DECLINE, 99.0);
        EvaluationResult persisted2 = transactionTemplate.execute(status ->
                persistenceService.persistDecisionAtomically(TENANT_A, event, result2, payloadHash)
        );

        // Assert second call returns the original decision
        assertThat(persisted2).isNotNull();
        assertThat(persisted2.decisionId()).isEqualTo(persisted1.decisionId());
        assertThat(persisted2.outcome()).isEqualTo(DecisionOutcome.REVIEW);

        // Verify database counts: exactly one row in each of the three tables
        transactionTemplate.execute(status -> {
            persistenceService.setTenantContext(TENANT_A);

            Integer decisionCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM decisions WHERE event_id = ?",
                    Integer.class, eventId
            );
            assertThat(decisionCount).isEqualTo(1);

            Integer processedCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM processed_events WHERE event_id = ?",
                    Integer.class, eventId
            );
            assertThat(processedCount).isEqualTo(1);

            Integer outboxCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM outbox WHERE aggregate_id = ?",
                    Integer.class, persisted1.decisionId().toString()
            );
            assertThat(outboxCount).isEqualTo(1);

            return null;
        });
    }

    private PaymentEvent createTestEvent(String eventId, UUID tenantId, long amountMinorUnits) {
        return new PaymentEvent(
                eventId,
                Instant.parse("2026-10-22T12:00:00Z"),
                1L,
                tenantId,
                "acc_hash_" + eventId,
                "tok_card_123",
                amountMinorUnits,
                "INR",
                "CARD",
                "m_1",
                "5411",
                "Merchant Store",
                "WEB",
                "127.0.0.1",
                "d_1",
                "agent",
                null,
                Map.of()
        );
    }

    private EvaluationResult createTestResult(String eventId, DecisionOutcome outcome, double score) {
        return new EvaluationResult(
                UUID.randomUUID(),
                eventId,
                outcome,
                score,
                outcome == DecisionOutcome.REVIEW,
                "ruleset-v1",
                "0000000000000000000000000000000000000000000000000000000000000000",
                List.of(new RuleOutcome("R1", "Rule 1", score, "CODE1")),
                Map.of("sample", "val"),
                false,
                null,
                2.4,
                Instant.parse("2026-10-22T12:00:00Z")
        );
    }
}
