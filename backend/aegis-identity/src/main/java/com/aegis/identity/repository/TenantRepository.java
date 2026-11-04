package com.aegis.identity.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TenantRepository {
    private final JdbcTemplate jdbcTemplate;

    public record TenantRecord(
        UUID tenantId,
        String slug,
        String name,
        String status
    ) {}

    public TenantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TenantRecord createTenant(UUID tenantId, String slug, String name) {
        UUID id = tenantId != null ? tenantId : UUID.randomUUID();
        String safeSlug = slug != null && !slug.isBlank() ? slug.trim().toLowerCase() : "tenant-" + id.toString().substring(0, 8);
        String sql = """
            INSERT INTO tenants (tenant_id, slug, name, status, fail_policy, sealer_interval_sec)
            VALUES (?, ?, ?, 'ACTIVE', 'FAIL_CLOSED', 60)
            RETURNING tenant_id, slug, name, status
        """;
        return jdbcTemplate.queryForObject(sql, (rs, i) -> new TenantRecord(
                (UUID) rs.getObject("tenant_id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("status")
        ), id, safeSlug, name.trim());
    }

    public Optional<TenantRecord> findById(UUID tenantId) {
        String sql = "SELECT tenant_id, slug, name, status FROM tenants WHERE tenant_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, i) -> new TenantRecord(
                    (UUID) rs.getObject("tenant_id"),
                    rs.getString("slug"),
                    rs.getString("name"),
                    rs.getString("status")
            ), tenantId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

