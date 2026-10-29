package com.aegis.identity.repository;

import com.aegis.identity.model.TenantUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthUserRepository {
    private final JdbcTemplate jdbcTemplate;

    public record UserRecord(
        UUID userId,
        UUID tenantId,
        String email,
        String mobile,
        String fullName,
        String role,
        String status,
        boolean totpEnabled,
        int failedLoginAttempts,
        Instant lockoutUntil
    ) {
        public TenantUser toTenantUser() {
            return new TenantUser(userId, email, mobile, fullName, role, status, totpEnabled);
        }
    }

    private final RowMapper<UserRecord> userRowMapper = (rs, rowNum) -> new UserRecord(
            (UUID) rs.getObject("user_id"),
            (UUID) rs.getObject("tenant_id"),
            rs.getString("email"),
            rs.getString("mobile"),
            rs.getString("full_name"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getBoolean("totp_enabled"),
            rs.getInt("failed_login_attempts"),
            rs.getTimestamp("lockout_until") != null ? rs.getTimestamp("lockout_until").toInstant() : null
    );

    public AuthUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserRecord> findById(UUID userId) {
        String sql = "SELECT * FROM aegis_auth.users WHERE user_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, userRowMapper, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<UserRecord> findByIdentifier(String identifier) {
        if (identifier == null) return Optional.empty();
        String trimmed = identifier.trim();
        String sql = "SELECT * FROM aegis_auth.users WHERE LOWER(email) = LOWER(?) OR mobile = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, userRowMapper, trimmed, trimmed));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<UserRecord> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String sql = "SELECT * FROM aegis_auth.users WHERE LOWER(email) = LOWER(?)";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, userRowMapper, email.trim()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<UserRecord> findByMobile(String mobile) {
        if (mobile == null) return Optional.empty();
        String sql = "SELECT * FROM aegis_auth.users WHERE mobile = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, userRowMapper, mobile.trim()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<TenantUser> findByTenantId(UUID tenantId) {
        String sql = "SELECT * FROM aegis_auth.users WHERE tenant_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, (rs, i) -> new TenantUser(
                (UUID) rs.getObject("user_id"),
                rs.getString("email"),
                rs.getString("mobile"),
                rs.getString("full_name"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getBoolean("totp_enabled")
        ), tenantId);
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM aegis_auth.users WHERE LOWER(email) = LOWER(?)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email.trim());
        return count != null && count > 0;
    }

    public boolean existsByMobile(String mobile) {
        String sql = "SELECT COUNT(*) FROM aegis_auth.users WHERE mobile = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, mobile.trim());
        return count != null && count > 0;
    }

    public UserRecord createUser(UUID userId, UUID tenantId, String email, String mobile, String fullName, String role, String status, boolean totpEnabled) {
        String sql = """
            INSERT INTO aegis_auth.users (user_id, tenant_id, email, mobile, full_name, role, status, totp_enabled)
            VALUES (?, ?, LOWER(?), ?, ?, ?, ?, ?)
            RETURNING *
        """;
        return jdbcTemplate.queryForObject(sql, userRowMapper,
                userId != null ? userId : UUID.randomUUID(),
                tenantId, email.trim(), mobile.trim(), fullName.trim(), role, status, totpEnabled);
    }

    public void updateStatusAndTotp(UUID userId, String status, boolean totpEnabled) {
        String sql = "UPDATE aegis_auth.users SET status = ?, totp_enabled = ?, updated_at = NOW() WHERE user_id = ?";
        jdbcTemplate.update(sql, status, totpEnabled, userId);
    }

    public void recordFailedLogin(UUID userId, Instant lockoutUntil) {
        String sql = """
            UPDATE aegis_auth.users
            SET failed_login_attempts = failed_login_attempts + 1,
                lockout_until = ?,
                updated_at = NOW()
            WHERE user_id = ?
        """;
        jdbcTemplate.update(sql, lockoutUntil != null ? Timestamp.from(lockoutUntil) : null, userId);
    }

    public void resetFailedLogins(UUID userId) {
        String sql = """
            UPDATE aegis_auth.users
            SET failed_login_attempts = 0,
                lockout_until = NULL,
                updated_at = NOW()
            WHERE user_id = ?
        """;
        jdbcTemplate.update(sql, userId);
    }
}

