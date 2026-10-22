package com.aegis.decision.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Secondary PAN control: scans free-text fields for Luhn-valid 13-19 digit card numbers (doc 01 §5.1).
 */
public final class LuhnPanScanner {
    private static final Pattern DIGIT_SEQUENCE = Pattern.compile("\\b(\\d{13,19})\\b");

    private LuhnPanScanner() {}

    public static boolean containsLuhnValidPan(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        // Check word-bounded contiguous digit strings
        Matcher matcher = DIGIT_SEQUENCE.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (isLuhnValid(candidate)) {
                return true;
            }
        }

        // Also check if text stripped of hyphens and spaces forms a Luhn-valid sequence
        String stripped = text.replaceAll("[\\s-]", "");
        Matcher strippedMatcher = DIGIT_SEQUENCE.matcher(stripped);
        while (strippedMatcher.find()) {
            String candidate = strippedMatcher.group(1);
            if (isLuhnValid(candidate)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isLuhnValid(String digits) {
        if (digits == null || digits.length() < 13 || digits.length() > 19) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (n < 0 || n > 9) {
                return false;
            }
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}
