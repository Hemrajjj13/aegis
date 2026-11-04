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
public class InviteRepository {
    private final JdbcTemplate jdbcTemplate;

    public record InviteRecord(
        UUID inviteId,
        UUID tenantId,
        String email,
        String role,
        UUID invitedByUserId,
        String tokenHash,
        String status,
        Instant expiresAt,
        Instant createdAt,
        Instant acceptedAt
    ) {}

    private final RowMapper<InviteRecord> inviteRowMapper = (rs, rowNum) -> new InviteRecord(
            (UUID) rs.getObject("invite_id"),
            (UUID) rs.getObject("tenant_id"),
            rs.getString("email"),
            rs.getString("role"),
            (UUID) rs.getObject("invited_by_user_id"),
            rs.getString("token_hash"),
            rs.getString("status"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("accepted_at") != null ? rs.getTimestamp("accepted_at").toInstant() : null
    );

    public InviteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public InviteRecord createInvite(UUID tenantId, String email, String role, UUID invitedByUserId, String tokenHash, Instant expiresAt) {
        String sql = """
            INSERT INTO aegis_auth.user_invites (tenant_id, email, role, invited_by_user_id, token_hash, status, expires_at)
            VALUES (?, LOWER(?), ?, ?, ?, 'PENDING', ?)
            RETURNING *
        """;
        return jdbcTemplate.queryForObject(sql, inviteRowMapper,
                tenantId, email.trim(), role, invitedByUserId, tokenHash, Timestamp.from(expiresAt));
    }

    public Optional<InviteRecord> findByTokenHash(String tokenHash) {
        String sql = "SELECT * FROM aegis_auth.user_invites WHERE token_hash = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, inviteRowMapper, tokenHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void markAccepted(UUID inviteId) {
        String sql = "UPDATE aegis_auth.user_invites SET status = 'ACCEPTED', accepted_at = NOW() WHERE invite_id = ?";
        jdbcTemplate.update(sql, inviteId);
    }
}

