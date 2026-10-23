package com.aegis.storage.dev;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Lightweight local development database utility for provisioning,
 * tenant seeding, and row-count verification without requiring external psql binaries.
 */
public class DevDatabaseTool {

    public static void main(String[] args) throws Exception {
        String command = args.length > 0 ? args[0] : "provision";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5432;

        switch (command) {
            case "provision" -> provisionDatabase(port);
            case "seed-tenant" -> seedTenant(port);
            case "row-counts" -> printRowCounts(port);
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    public static void provisionDatabase(int port) throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
        try (Connection conn = DriverManager.getConnection(url, "postgres", "")) {
            try (Statement stmt = conn.createStatement()) {
                // Check if role aegis exists
                ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_roles WHERE rolname = 'aegis'");
                if (!rs.next()) {
                    stmt.execute("CREATE USER aegis WITH SUPERUSER PASSWORD 'aegis'");
                    System.out.println("Role 'aegis' created.");
                } else {
                    System.out.println("Role 'aegis' already exists.");
                }

                // Check if database aegis exists
                rs = stmt.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'aegis'");
                if (!rs.next()) {
                    stmt.execute("CREATE DATABASE aegis OWNER aegis");
                    System.out.println("Database 'aegis' created.");
                } else {
                    System.out.println("Database 'aegis' already exists.");
                }
            }
        }
    }

    public static void seedTenant(int port) throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/aegis";
        try (Connection conn = DriverManager.getConnection(url, "aegis", "aegis")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    INSERT INTO tenants (tenant_id, slug, name, status, fail_policy, sealer_interval_sec)
                    VALUES ('00000000-0000-0000-0000-000000000001', 'acme_pay', 'Acme Payments India', 'ACTIVE', 'FAIL_OPEN', 30)
                    ON CONFLICT (tenant_id) DO NOTHING
                """);
                System.out.println("Seeded dev tenant 00000000-0000-0000-0000-000000000001.");
            }
        }
    }

    public static void printRowCounts(int port) throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/aegis";
        try (Connection conn = DriverManager.getConnection(url, "aegis", "aegis")) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("""
                    SELECT 'tenants' AS table_name, count(*) AS row_count FROM tenants
                    UNION ALL
                    SELECT 'processed_events', count(*) FROM processed_events
                    UNION ALL
                    SELECT 'decisions', count(*) FROM decisions
                    UNION ALL
                    SELECT 'outbox', count(*) FROM outbox
                """);
                System.out.println("----------------------------------------");
                System.out.printf("%-20s | %-10s%n", "table_name", "row_count");
                System.out.println("---------------------+------------------");
                while (rs.next()) {
                    System.out.printf("%-20s | %-10d%n", rs.getString("table_name"), rs.getLong("row_count"));
                }
                System.out.println("----------------------------------------");
            }
        }
    }
}
