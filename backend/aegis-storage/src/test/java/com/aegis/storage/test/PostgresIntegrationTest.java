package com.aegis.storage.test;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;

public abstract class PostgresIntegrationTest {
    protected static final PostgreSQLContainer<?> DOCKER_POSTGRES;
    protected static final String JDBC_URL;
    protected static final String USERNAME;
    protected static final String PASSWORD;

    static {
        if (DockerAvailability.isAvailable()) {
            DOCKER_POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("aegis")
                    .withUsername("aegis")
                    .withPassword("aegis");
            DOCKER_POSTGRES.start();
            JDBC_URL = DOCKER_POSTGRES.getJdbcUrl();
            USERNAME = DOCKER_POSTGRES.getUsername();
            PASSWORD = DOCKER_POSTGRES.getPassword();
        } else {
            DOCKER_POSTGRES = null;
            String url = System.getenv("AEGIS_PG_URL");
            if (url == null || url.isBlank()) {
                url = System.getProperty("AEGIS_PG_URL");
            }
            if (url == null || url.isBlank()) {
                url = "jdbc:postgresql://127.0.0.1:5432/aegis";
            }

            String user = System.getenv("AEGIS_PG_USER");
            if (user == null || user.isBlank()) {
                user = System.getProperty("AEGIS_PG_USER");
            }
            if (user == null || user.isBlank()) {
                user = "aegis";
            }

            String pass = System.getenv("AEGIS_PG_PASSWORD");
            if (pass == null) {
                pass = System.getProperty("AEGIS_PG_PASSWORD");
            }
            if (pass == null) {
                pass = "aegis";
            }

            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                JDBC_URL = url;
                USERNAME = user;
                PASSWORD = pass;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Database integration tests require either Docker (Testcontainers) or an installed PostgreSQL 16 instance.\n" +
                        "Neither Docker is running nor could a connection be established to PostgreSQL at: " + url + "\n" +
                        "Error: " + e.getMessage() + "\n\n" +
                        "SETUP INSTRUCTIONS:\n" +
                        "  1. Start Docker Desktop (if using Docker/Testcontainers), OR\n" +
                        "  2. Install/start PostgreSQL 16 locally on port 5432 and set environment variables:\n" +
                        "       AEGIS_PG_URL=jdbc:postgresql://127.0.0.1:5432/aegis\n" +
                        "       AEGIS_PG_USER=aegis\n" +
                        "       AEGIS_PG_PASSWORD=aegis\n" +
                        "See README.md for full local development setup.", e);
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
