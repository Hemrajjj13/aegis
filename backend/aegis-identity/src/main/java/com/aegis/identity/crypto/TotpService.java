package com.aegis.identity.crypto;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;

@Service
public class TotpService {
    private static final Logger log = LoggerFactory.getLogger(TotpService.class);

    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12;   // bytes
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Environment environment;
    private final GoogleAuthenticator gAuth;
    private SecretKey encryptionKey;

    public record EncryptedSecret(byte[] ciphertext, byte[] iv) {}
    public record RecoveryCodesBundle(List<String> rawCodes, List<String> hashedCodes) {}

    public TotpService(Environment environment) {
        this.environment = environment;
        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setTimeStepSizeInMillis(30_000)
                .setWindowSize(3) // current step +- 1
                .setCodeDigits(6)
                .build();
        this.gAuth = new GoogleAuthenticator(config);
    }

    @PostConstruct
    public void init() {
        boolean isTier0 = isProfileActive("tier0");
        String envKey = environment != null ? environment.getProperty("AEGIS_TOTP_ENC_KEY") : System.getenv("AEGIS_TOTP_ENC_KEY");

        if (envKey != null && !envKey.isBlank()) {
            this.encryptionKey = parseKey(envKey.trim());
            return;
        }

        if (isTier0) {
            initTier0Key();
        } else {
            throw new IllegalStateException(
                "FATAL: AEGIS_TOTP_ENC_KEY must be configured in tier1/tier2 profiles."
            );
        }
    }

    private boolean isProfileActive(String profile) {
        if (environment == null || environment.getActiveProfiles() == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }

    private void initTier0Key() {
        try {
            Path devKeysDir = Path.of(".dev-keys");
            if (!Files.exists(devKeysDir)) {
                Files.createDirectories(devKeysDir);
            }
            Path keyPath = devKeysDir.resolve("totp.key");
            if (Files.exists(keyPath)) {
                byte[] raw = HexFormat.of().parseHex(Files.readString(keyPath).trim());
                this.encryptionKey = new SecretKeySpec(raw, "AES");
                log.info("Loaded tier0 TOTP encryption key from {}", keyPath.toAbsolutePath());
            } else {
                byte[] raw = new byte[32];
                SECURE_RANDOM.nextBytes(raw);
                this.encryptionKey = new SecretKeySpec(raw, "AES");
                Files.writeString(keyPath, HexFormat.of().formatHex(raw));
                log.info("Generated new tier0 TOTP encryption key in {}", keyPath.toAbsolutePath());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize tier0 TOTP encryption key", e);
        }
    }

    private SecretKey parseKey(String keyStr) {
        byte[] bytes;
        if (keyStr.length() == 64) {
            bytes = HexFormat.of().parseHex(keyStr);
        } else {
            bytes = Base64.getDecoder().decode(keyStr);
        }
        if (bytes.length != 32) {
            throw new IllegalArgumentException("AEGIS_TOTP_ENC_KEY must be 32 bytes (256-bit AES)");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    public String generateSecret() {
        GoogleAuthenticatorKey credentials = gAuth.createCredentials();
        return credentials.getKey();
    }

    public String buildOtpAuthUri(String secret, String email) {
        String encodedIssuer = URLEncoder.encode("Project Aegis", StandardCharsets.UTF_8);
        String encodedAccount = URLEncoder.encode(email, StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                encodedIssuer, encodedAccount, secret, encodedIssuer);
    }

    public boolean verifyCode(String secret, int code) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        return gAuth.authorize(secret, code);
    }

    public boolean verifyCodeString(String secret, String codeStr) {
        if (secret == null || codeStr == null || codeStr.isBlank()) {
            return false;
        }
        try {
            int code = Integer.parseInt(codeStr.trim());
            return verifyCode(secret, code);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public EncryptedSecret encryptSecret(String secret) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, spec);

            byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(ciphertext, iv);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    public String decryptSecret(byte[] ciphertext, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", e);
        }
    }

    public RecoveryCodesBundle generateRecoveryCodes() {
        List<String> rawCodes = CryptoUtils.generateRecoveryCodes(10, 8);
        List<String> hashedCodes = rawCodes.stream()
                .map(CryptoUtils::sha256Hex)
                .toList();
        return new RecoveryCodesBundle(rawCodes, hashedCodes);
    }
}

