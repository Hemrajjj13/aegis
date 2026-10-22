package com.aegis.storage.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayScriptsValidationTest {

    @Test
    @DisplayName("Verify Flyway migration scripts V1 through V4 exist, are non-empty, and contain RLS & FK constraints")
    void testFlywayScriptsIntegrity() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/V*__*.sql");

        assertThat(resources).hasSizeGreaterThanOrEqualTo(3);

        List<String> filenames = Arrays.stream(resources)
                .map(Resource::getFilename)
                .sorted()
                .toList();

        assertThat(filenames).contains(
                "V1__init_tenants_and_roles.sql",
                "V2__init_processed_events.sql",
                "V3__init_decisions_partitioned.sql",
                "V4__init_transactional_outbox.sql"
        );

        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(sql).isNotBlank();

                if (resource.getFilename().equals("V1__init_tenants_and_roles.sql")) {
                    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tenants");
                    assertThat(sql).contains("tenant_id           UUID PRIMARY KEY");
                }
                if (resource.getFilename().equals("V2__init_processed_events.sql")) {
                    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS processed_events");
                    assertThat(sql).contains("REFERENCES tenants(tenant_id)");
                    assertThat(sql).contains("ENABLE ROW LEVEL SECURITY");
                    assertThat(sql).contains("FORCE ROW LEVEL SECURITY");
                    assertThat(sql).contains("CREATE POLICY tenant_isolation_processed_events");
                }
                if (resource.getFilename().equals("V3__init_decisions_partitioned.sql")) {
                    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS decisions");
                    assertThat(sql).contains("PARTITION BY RANGE (created_at)");
                    assertThat(sql).contains("ENABLE ROW LEVEL SECURITY");
                    assertThat(sql).contains("FORCE ROW LEVEL SECURITY");
                    assertThat(sql).contains("CREATE POLICY tenant_isolation_decisions");
                }
                if (resource.getFilename().equals("V4__init_transactional_outbox.sql")) {
                    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS outbox");
                    assertThat(sql).contains("ENABLE ROW LEVEL SECURITY");
                    assertThat(sql).contains("FORCE ROW LEVEL SECURITY");
                }
            }
        }
    }
}
