package com.aegis.storage.test;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;

public abstract class PostgresIntegrationTest {
    protected static final PostgreSQLContainer<?> DOCKER_POSTGRES;
    protected static final EmbeddedPostgres EMBEDDED_POSTGRES;
    protected static final String JDBC_URL;
    protected static final String USERNAME;
    protected static final String PASSWORD;

    static {
        if (DockerAvailability.isAvailable()) {
            DOCKER_POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("aegis_db")
                    .withUsername("aegis_user")
                    .withPassword("aegis_local_password");
            DOCKER_POSTGRES.start();
            EMBEDDED_POSTGRES = null;
            JDBC_URL = DOCKER_POSTGRES.getJdbcUrl();
            USERNAME = DOCKER_POSTGRES.getUsername();
            PASSWORD = DOCKER_POSTGRES.getPassword();
        } else {
            DOCKER_POSTGRES = null;
            try {
                EMBEDDED_POSTGRES = EmbeddedPostgres.builder().start();
                JDBC_URL = String.format("jdbc:postgresql://localhost:%d/postgres", EMBEDDED_POSTGRES.getPort());
                USERNAME = "postgres";
                PASSWORD = "postgres";
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (EMBEDDED_POSTGRES != null) {
                        try {
                            EMBEDDED_POSTGRES.close();
                        } catch (IOException ignored) {}
                    }
                }));
            } catch (Exception e) {
                throw new RuntimeException("Failed to start fallback EmbeddedPostgres instance", e);
            }
        }
    }

    public static String getJdbcUrl() {
        return JDBC_URL;
    }

    public static String getUsername() {
        return USERNAME;
    }

    public static String getPassword() {
        return PASSWORD;
    }

    public static boolean isUsingDocker() {
        return DOCKER_POSTGRES != null;
    }
}

