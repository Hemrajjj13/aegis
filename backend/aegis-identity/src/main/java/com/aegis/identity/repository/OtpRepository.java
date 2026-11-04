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
public class OtpRepository {
    private final JdbcTemplate jdbcTemplate;

    public record OtpRecord(
        UUID otpId,
        UUID tenantId,
        String recipientIdentifier,
        String otpPurpose,
        String otpHash,
        int attemptCount,
        int maxAttempts,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt
    ) {}

    private final RowMapper<OtpRecord> otpRowMapper = (rs, rowNum) -> new OtpRecord(
            (UUID) rs.getObject("otp_id"),
            (UUID) rs.getObject("tenant_id"),
            rs.getString("recipient_identifier"),
            rs.getString("otp_purpose"),
            rs.getString("otp_hash"),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("consumed_at") != null ? rs.getTimestamp("consumed_at").toInstant() : null,
            rs.getTimestamp("created_at").toInstant()
    );

    public OtpRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OtpRecord createOtp(UUID tenantId, String recipientIdentifier, String purpose, String otpHash, Instant expiresAt) {
        String sql = """
            INSERT INTO aegis_auth.auth_otp_codes (tenant_id, recipient_identifier, otp_purpose, otp_hash, attempt_count, max_attempts, expires_at)
            VALUES (?, ?, ?, ?, 0, 3, ?)
            RETURNING *
        """;
        return jdbcTemplate.queryForObject(sql, otpRowMapper,
                tenantId, recipientIdentifier.trim(), purpose, otpHash, Timestamp.from(expiresAt));
    }

    public Optional<OtpRecord> findActiveById(UUID otpId) {
        String sql = """
            SELECT * FROM aegis_auth.auth_otp_codes
            WHERE otp_id = ? AND consumed_at IS NULL AND expires_at > NOW() AND attempt_count < max_attempts
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, otpRowMapper, otpId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<OtpRecord> findLatestActive(String recipientIdentifier, String purpose) {
        String sql = """
            SELECT * FROM aegis_auth.auth_otp_codes
            WHERE recipient_identifier = ? AND otp_purpose = ? AND consumed_at IS NULL AND expires_at > NOW() AND attempt_count < max_attempts
            ORDER BY created_at DESC LIMIT 1
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, otpRowMapper, recipientIdentifier.trim(), purpose));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void incrementAttempt(UUID otpId) {
        String sql = "UPDATE aegis_auth.auth_otp_codes SET attempt_count = attempt_count + 1 WHERE otp_id = ?";
        jdbcTemplate.update(sql, otpId);
    }

    public void markConsumed(UUID otpId) {
        String sql = "UPDATE aegis_auth.auth_otp_codes SET consumed_at = NOW() WHERE otp_id = ?";
        jdbcTemplate.update(sql, otpId);
    }
}

