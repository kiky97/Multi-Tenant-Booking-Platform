package com.booking.engine.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts contact-like data from operational log details while preserving
 * enough
 * structure for debugging.
 */
public final class SensitiveLogSanitizer {

    private static final int HASH_LENGTH = 12;
    private static final int MAX_LOG_LENGTH = 500;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)\\b[\\p{Alnum}._%+\\-]+@[\\p{Alnum}.\\-]+\\.[a-z]{2,63}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<![\\p{Alnum}])(?:\\+?[0-9][0-9()\\-\\s]{6,}[0-9])(?![\\p{Alnum}])");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern AUTHORIZATION_BEARER_PATTERN = Pattern.compile(
            "(?i)\\bAuthorization\\s*[:=]\\s*Bearer\\s+[^\\s,;]+");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)\\bBearer\\s+[^\\s,;]+");
    private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(api[_-]?key|token|access[_-]?token|refresh[_-]?token|id[_-]?token"
                    + "|client[_-]?secret|clientsecret|secret|password|payment[_-]?method)\\b"
                    + "(\\s*[:=]\\s*)[^\\s,;]+");

    private SensitiveLogSanitizer() {
    }

    public static String sanitizeForLogs(String value) {
        if (value == null || value.isBlank()) {
            return "n/a";
        }

        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        String sanitized = redactSensitiveValues(redactPhoneCandidates(redactEmails(normalized)));
        return sanitized.length() <= MAX_LOG_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LOG_LENGTH);
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        String normalized = email.trim();
        int atIndex = normalized.indexOf('@');
        if (atIndex <= 0 || atIndex == normalized.length() - 1) {
            return "***";
        }

        String localPart = normalized.substring(0, atIndex);
        String domainPart = normalized.substring(atIndex + 1);
        if (localPart.length() == 1) {
            return "*@" + domainPart;
        }

        if (localPart.length() == 2) {
            return localPart.charAt(0) + "*@" + domainPart;
        }

        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + "@" + domainPart;
    }

    public static String hashValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte currentByte : hash) {
                builder.append(String.format("%02x", currentByte));
            }
            return builder.substring(0, Math.min(HASH_LENGTH, builder.length()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 support is required for security audit hashing.", exception);
        }
    }

    public static String hashPhone(String phone) {
        String digitsOnly = digitsOnly(phone);
        return digitsOnly == null ? null : hashValue(digitsOnly);
    }

    private static String redactEmails(String value) {
        Matcher matcher = EMAIL_PATTERN.matcher(value);
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    sanitized,
                    Matcher.quoteReplacement("[emailMask=" + maskEmail(matcher.group()) + "]"));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    private static String redactPhoneCandidates(String value) {
        Matcher matcher = PHONE_PATTERN.matcher(value);
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            String candidate = matcher.group();
            String replacement = looksLikePhone(candidate)
                    ? "[phoneHash=" + hashPhone(candidate) + "]"
                    : candidate;
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    private static String redactSensitiveValues(String value) {
        return redactSensitiveAssignments(redactBearerTokens(redactAuthorizationBearer(value)));
    }

    private static String redactAuthorizationBearer(String value) {
        return AUTHORIZATION_BEARER_PATTERN.matcher(value).replaceAll("Authorization=[redacted]");
    }

    private static String redactBearerTokens(String value) {
        return BEARER_TOKEN_PATTERN.matcher(value).replaceAll("Bearer [redacted]");
    }

    private static String redactSensitiveAssignments(String value) {
        Matcher matcher = SENSITIVE_ASSIGNMENT_PATTERN.matcher(value);
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    sanitized,
                    Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + "[redacted]"));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    private static boolean looksLikePhone(String candidate) {
        String normalized = candidate == null ? "" : candidate.trim();
        if (normalized.isEmpty() || DATE_PATTERN.matcher(normalized).matches()) {
            return false;
        }

        String digitsOnly = digitsOnly(normalized);
        if (digitsOnly == null || digitsOnly.length() < 7) {
            return false;
        }

        return normalized.contains("+")
                || normalized.matches(".*[()\\-\\s].*")
                || digitsOnly.length() >= 10;
    }

    private static String digitsOnly(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String digitsOnly = value.replaceAll("[^0-9]", "");
        return digitsOnly.isBlank() ? null : digitsOnly;
    }
}
