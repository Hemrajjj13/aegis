package com.aegis.storage.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIntegrationTest extends PostgresIntegrationTest {

    @Test
    @DisplayName("Verify V1 to V3 (and V4) Flyway migrations apply cleanly and establish schema")
    void testFlywayMigrationsApplySuccessfully() throws Exception {
        String jdbcUrl = getJdbcUrl();
        String username = getUsername();
        String password = getPassword();

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .outOfOrder(true)
                .load();

        flyway.clean();
        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(3);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement()) {

            // Verify tenants table exists and contains seeded tenants
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM tenants")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(2);
            }

            // Verify processed_events table exists
            try (ResultSet rs = stmt.executeQuery("SELECT to_regclass('public.processed_events')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("processed_events");
            }

            // Verify partitioned decisions table and partitions exist
            try (ResultSet rs = stmt.executeQuery("SELECT to_regclass('public.decisions')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("decisions");
            }

            // Verify outbox table exists
            try (ResultSet rs = stmt.executeQuery("SELECT to_regclass('public.outbox')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("outbox");
            }

            // Verify Row-Level Security is enabled and forced
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname IN ('processed_events', 'decisions', 'outbox')")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat(rs.getBoolean("relrowsecurity")).isTrue();
                    assertThat(rs.getBoolean("relforcerowsecurity")).isTrue();
                }
                assertThat(count).isEqualTo(3);
            }
        }
    }
}
