package com.aegis.identity.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class CryptoUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] CODE_CHARS = "0123456789".toCharArray();
    private static final char[] RECOVERY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private CryptoUtils() {}

    public static String sha256Hex(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }

    public static String secureRandomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String generate6DigitCode() {
        char[] buf = new char[6];
        for (int i = 0; i < 6; i++) {
            buf[i] = CODE_CHARS[SECURE_RANDOM.nextInt(CODE_CHARS.length)];
        }
        return new String(buf);
    }

    public static List<String> generateRecoveryCodes(int count, int length) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            char[] buf = new char[length];
            for (int j = 0; j < length; j++) {
                buf[j] = RECOVERY_CHARS[SECURE_RANDOM.nextInt(RECOVERY_CHARS.length)];
            }
            codes.add(new String(buf));
        }
        return codes;
    }
}

