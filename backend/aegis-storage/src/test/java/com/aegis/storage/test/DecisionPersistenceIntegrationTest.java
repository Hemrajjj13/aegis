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

import java.sql.Connection;
import java.sql.Statement;
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
    void setUp() throws Exception {
        // 1. Run migrations as admin/superuser
        var adminDataSource = new DriverManagerDataSource();
        adminDataSource.setDriverClassName("org.postgresql.Driver");
        adminDataSource.setUrl(getJdbcUrl());
        adminDataSource.setUsername(getUsername());
        adminDataSource.setPassword(getPassword());

        Flyway flyway = Flyway.configure()
                .dataSource(adminDataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        // 2. Grant permissions to app_live_user (non-superuser)
        try (Connection conn = adminDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER ROLE app_live_user WITH LOGIN PASSWORD 'app_live_pass'");
            stmt.execute("GRANT USAGE ON SCHEMA public TO app_live_user");
            stmt.execute("GRANT SELECT ON TABLE tenants TO app_live_user");
            stmt.execute("GRANT SELECT, INSERT ON TABLE processed_events, decisions, outbox TO app_live_user");
            stmt.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_live_user");
        }

        // 3. Connect as non-superuser app_live_user
        var appLiveDataSource = new DriverManagerDataSource();
        appLiveDataSource.setDriverClassName("org.postgresql.Driver");
        appLiveDataSource.setUrl(getJdbcUrl());
        appLiveDataSource.setUsername("app_live_user");
        appLiveDataSource.setPassword("app_live_pass");

        this.jdbcTemplate = new JdbcTemplate(appLiveDataSource);
        var txManager = new DataSourceTransactionManager(appLiveDataSource);
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.persistenceService = new JdbcDecisionPersistenceService(jdbcTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("Verify persistence tests connect as the non-superuser role app_live_user")
    void testConnectedAsNonSuperuserAppLiveUser() {
        String currentUser = jdbcTemplate.queryForObject("SELECT current_user", String.class);
        assertThat(currentUser).isEqualTo("app_live_user");

        Boolean isSuperuser = jdbcTemplate.queryForObject(
                "SELECT usesuper FROM pg_user WHERE usename = current_user", Boolean.class);
        assertThat(isSuperuser).isFalse();
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
        var persisted1 = transactionTemplate.execute(status ->
                persistenceService.persistDecisionAtomically(TENANT_A, event, result1, payloadHash)
        );
        assertThat(persisted1).isNotNull();
        assertThat(persisted1.duplicate()).isFalse();

        // Second submission with identical payload hash and different result instance
        EvaluationResult result2 = createTestResult(eventId, DecisionOutcome.DECLINE, 99.0);
        var persisted2 = transactionTemplate.execute(status ->
                persistenceService.persistDecisionAtomically(TENANT_A, event, result2, payloadHash)
        );

        // Assert second call returns the original decision with duplicate=true
        assertThat(persisted2).isNotNull();
        assertThat(persisted2.duplicate()).isTrue();
        assertThat(persisted2.result().decisionId()).isEqualTo(persisted1.result().decisionId());
        assertThat(persisted2.result().outcome()).isEqualTo(DecisionOutcome.REVIEW);

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
                    Integer.class, persisted1.result().decisionId().toString()
            );
            assertThat(outboxCount).isEqualTo(1);

            return null;
        });
    }

    @Test
    @DisplayName("A failure mid-transaction leaves nothing behind (atomicity verification)")
    void testFailureMidTransactionLeavesNothingBehind() {
        String failEventId = "evt_fail_tx_test_1";
        PaymentEvent failEvent = createTestEvent(failEventId, TENANT_A, 2500000L);
        EvaluationResult failResult = createTestResult(failEventId, DecisionOutcome.APPROVE, 20.0);
        String payloadHash = "sha256_fail_tx_hash";

        try {
            transactionTemplate.execute(status -> {
                persistenceService.persistDecisionAtomically(TENANT_A, failEvent, failResult, payloadHash);
                throw new RuntimeException("Simulated mid-transaction failure for rollback test");
            });
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage()).contains("Simulated mid-transaction failure");
        }

        // Verify nothing was left behind in decisions, processed_events, or outbox
        transactionTemplate.execute(status -> {
            persistenceService.setTenantContext(TENANT_A);

            Integer decisionCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM decisions WHERE event_id = ?",
                    Integer.class, failEventId
            );
            assertThat(decisionCount).isZero();

            Integer processedCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM processed_events WHERE event_id = ?",
                    Integer.class, failEventId
            );
            assertThat(processedCount).isZero();

            Integer outboxCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM outbox WHERE aggregate_id = ?",
                    Integer.class, failResult.decisionId().toString()
            );
            assertThat(outboxCount).isZero();

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
