package com.aegis.storage.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayScriptsValidationTest extends PostgresIntegrationTest {

    @Test
    @DisplayName("Verify Flyway migrations V1 to V4 run and assert tables, RLS flags and FORCE ROW LEVEL SECURITY")
    void testFlywayMigrationsV1ToV4ExecutionAndRlsFlags() throws Exception {
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
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(4);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement()) {

            // 1. Assert all tables exist
            for (String tableName : new String[]{"tenants", "processed_events", "decisions", "outbox"}) {
                try (ResultSet rs = stmt.executeQuery("SELECT to_regclass('public." + tableName + "')")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo(tableName);
                }
            }

            // 2. Assert seeded tenants exist
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM tenants")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(2);
            }

            // 3. Assert RLS flags: relrowsecurity and relforcerowsecurity
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class " +
                            "WHERE relname IN ('processed_events', 'decisions', 'outbox')")) {
                int verifiedCount = 0;
                while (rs.next()) {
                    verifiedCount++;
                    String relname = rs.getString("relname");
                    boolean rowSecurity = rs.getBoolean("relrowsecurity");
                    boolean forceRowSecurity = rs.getBoolean("relforcerowsecurity");

                    assertThat(rowSecurity)
                            .as("relrowsecurity for table " + relname)
                            .isTrue();
                    assertThat(forceRowSecurity)
                            .as("relforcerowsecurity for table " + relname)
                            .isTrue();
                }
                assertThat(verifiedCount).isEqualTo(3);
            }
        }
    }
}
