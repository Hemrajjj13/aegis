package com.aegis.identity.crypto;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class Argon2Service {
    // OWASP 2024 recommendations: m=65536 KiB (64 MiB), t=3 iterations, p=4 parallelism, 16-byte salt, 32-byte hash
    private final Argon2PasswordEncoder encoder;
    private final String dummyHash;

    public Argon2Service() {
        this.encoder = new Argon2PasswordEncoder(16, 32, 4, 65536, 3);
        this.dummyHash = this.encoder.encode("dummy_password_timing_equalization_2026");
    }

    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null) {
            return false;
        }
        return encoder.matches(rawPassword, encodedHash);
    }

    /**
     * Executes work identical to matches() against a precomputed Argon2id hash
     * to eliminate timing differences between existing and non-existing accounts.
     */
    public boolean verifyDummy(String rawPassword) {
        if (rawPassword == null) {
            rawPassword = "";
        }
        return encoder.matches(rawPassword, dummyHash);
    }
}

