package com.aegis.identity.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SessionRepository {
    private final JdbcTemplate jdbcTemplate;

    public record SessionRecord(
        UUID sessionId,
        UUID userId,
        UUID tenantId,
        String refreshTokenHash,
        String ipAddress,
        String userAgent,
        boolean isRevoked,
        Instant expiresAt,
        Instant lastActivityAt,
        Instant createdAt
    ) {}

    private final RowMapper<SessionRecord> sessionRowMapper = (rs, rowNum) -> new SessionRecord(
            (UUID) rs.getObject("session_id"),
            (UUID) rs.getObject("user_id"),
            (UUID) rs.getObject("tenant_id"),
            rs.getString("refresh_token_hash"),
            rs.getString("ip_address"),
            rs.getString("user_agent"),
            rs.getBoolean("is_revoked"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("last_activity_at").toInstant(),
            rs.getTimestamp("created_at").toInstant()
    );

    public SessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SessionRecord createSession(UUID userId, UUID tenantId, String refreshTokenHash, String ipAddress, String userAgent, Instant expiresAt) {
        String safeIp = (ipAddress != null && !ipAddress.isBlank()) ? ipAddress.trim() : "127.0.0.1";
        if (safeIp.contains("%")) {
            safeIp = safeIp.substring(0, safeIp.indexOf('%'));
        }
        String sql = """
            INSERT INTO aegis_auth.user_sessions (user_id, tenant_id, refresh_token_hash, ip_address, user_agent, is_revoked, expires_at)
            VALUES (?, ?, ?, CAST(? AS inet), ?, FALSE, ?)
            RETURNING *
        """;
        return jdbcTemplate.queryForObject(sql, sessionRowMapper,
                userId, tenantId, refreshTokenHash, safeIp,
                userAgent != null ? userAgent : "unknown",
                Timestamp.from(expiresAt));
    }

    public Optional<SessionRecord> findByRefreshTokenHash(String refreshTokenHash) {
        String sql = "SELECT * FROM aegis_auth.user_sessions WHERE refresh_token_hash = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, sessionRowMapper, refreshTokenHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void rotateRefreshToken(UUID sessionId, String newRefreshTokenHash, Instant newExpiresAt) {
        String sql = """
            UPDATE aegis_auth.user_sessions
            SET refresh_token_hash = ?,
                expires_at = ?,
                last_activity_at = NOW()
            WHERE session_id = ?
        """;
        jdbcTemplate.update(sql, newRefreshTokenHash, Timestamp.from(newExpiresAt), sessionId);
    }

    public void revokeSession(UUID sessionId) {
        String sql = "UPDATE aegis_auth.user_sessions SET is_revoked = TRUE WHERE session_id = ?";
        jdbcTemplate.update(sql, sessionId);
    }

    public void revokeAllUserSessions(UUID userId) {
        String sql = "UPDATE aegis_auth.user_sessions SET is_revoked = TRUE WHERE user_id = ?";
        jdbcTemplate.update(sql, userId);
    }
}

