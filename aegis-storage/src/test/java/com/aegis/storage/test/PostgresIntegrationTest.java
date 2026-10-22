package com.aegis.storage.test;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class PostgresIntegrationTest {
    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (DockerAvailability.isAvailable()) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("aegis_db")
                    .withUsername("aegis_user")
                    .withPassword("aegis_local_password");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @BeforeAll
    static void checkDocker() {
        Assumptions.assumeTrue(DockerAvailability.isAvailable(), "Docker daemon unavailable; skipping live container test");
    }

    public static String getJdbcUrl() {
        return POSTGRES != null ? POSTGRES.getJdbcUrl() : null;
    }

    public static String getUsername() {
        return POSTGRES != null ? POSTGRES.getUsername() : null;
    }

    public static String getPassword() {
        return POSTGRES != null ? POSTGRES.getPassword() : null;
    }
}
