package com.aegis.identity.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Connection;
import java.util.*;

@Repository
public class TotpSecretRepository {
    private final JdbcTemplate jdbcTemplate;

    public record TotpSecretRecord(
        UUID userId,
        byte[] encryptedSecret,
        byte[] iv,
        List<String> recoveryCodeHashes
    ) {}

    public TotpSecretRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(UUID userId, byte[] encryptedSecret, byte[] iv, List<String> recoveryCodeHashes) {
        String sql = """
            INSERT INTO aegis_auth.user_totp_secrets (user_id, secret_key_encrypted, initialization_vector, recovery_codes_hash)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET
                secret_key_encrypted = EXCLUDED.secret_key_encrypted,
                initialization_vector = EXCLUDED.initialization_vector,
                recovery_codes_hash = EXCLUDED.recovery_codes_hash,
                created_at = NOW()
        """;
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql);
            ps.setObject(1, userId);
            ps.setBytes(2, encryptedSecret);
            ps.setBytes(3, iv);
            Array sqlArray = connection.createArrayOf("text", recoveryCodeHashes.toArray(new String[0]));
            ps.setArray(4, sqlArray);
            return ps;
        });
    }

    public Optional<TotpSecretRecord> findByUserId(UUID userId) {
        String sql = "SELECT * FROM aegis_auth.user_totp_secrets WHERE user_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Array array = rs.getArray("recovery_codes_hash");
                String[] codes = array != null ? (String[]) array.getArray() : new String[0];
                return new TotpSecretRecord(
                        (UUID) rs.getObject("user_id"),
                        rs.getBytes("secret_key_encrypted"),
                        rs.getBytes("initialization_vector"),
                        new ArrayList<>(Arrays.asList(codes))
                );
            }, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void removeRecoveryCode(UUID userId, String usedCodeHash) {
        var existing = findByUserId(userId);
        if (existing.isPresent()) {
            List<String> updated = new ArrayList<>(existing.get().recoveryCodeHashes());
            updated.remove(usedCodeHash);
            String sql = "UPDATE aegis_auth.user_totp_secrets SET recovery_codes_hash = ? WHERE user_id = ?";
            jdbcTemplate.update(connection -> {
                var ps = connection.prepareStatement(sql);
                Array sqlArray = connection.createArrayOf("text", updated.toArray(new String[0]));
                ps.setArray(1, sqlArray);
                ps.setObject(2, userId);
                return ps;
            });
        }
    }
}

