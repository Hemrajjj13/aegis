package com.aegis.identity.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CredentialsRepository {
    private final JdbcTemplate jdbcTemplate;

    public CredentialsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(UUID userId, String passwordHash) {
        String sql = """
            INSERT INTO aegis_auth.user_credentials (user_id, password_hash, password_updated_at, must_change_password)
            VALUES (?, ?, NOW(), FALSE)
            ON CONFLICT (user_id) DO UPDATE SET password_hash = EXCLUDED.password_hash, password_updated_at = NOW()
        """;
        jdbcTemplate.update(sql, userId, passwordHash);
    }

    public Optional<String> findPasswordHashByUserId(UUID userId) {
        String sql = "SELECT password_hash FROM aegis_auth.user_credentials WHERE user_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, String.class, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void updatePassword(UUID userId, String passwordHash) {
        String sql = "UPDATE aegis_auth.user_credentials SET password_hash = ?, password_updated_at = NOW() WHERE user_id = ?";
        jdbcTemplate.update(sql, passwordHash, userId);
    }
}

