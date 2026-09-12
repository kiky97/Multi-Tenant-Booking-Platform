package com.booking.engine.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Generates and verifies public hold access tokens without persisting raw
 * token material.
 */
@Component
public class BookingHoldAccessTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String token) {
        if (token == null || token.trim().isBlank()) {
            throw new IllegalArgumentException("Hold access token is required");
        }

        return toHex(digest(token.trim()));
    }

    public boolean matches(String rawToken, String expectedHash) {
        if (rawToken == null || rawToken.trim().isBlank()
                || expectedHash == null || expectedHash.trim().isBlank()) {
            return false;
        }

        byte[] expectedHashBytes;
        try {
            expectedHashBytes = fromHex(expectedHash.trim());
        } catch (IllegalArgumentException exception) {
            return false;
        }

        return MessageDigest.isEqual(digest(rawToken.trim()), expectedHashBytes);
    }

    private byte[] digest(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index += 1) {
            int value = bytes[index] & 0xff;
            chars[index * 2] = HEX[value >>> 4];
            chars[index * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(chars);
    }

    private byte[] fromHex(String value) {
        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex value");
        }

        byte[] bytes = new byte[value.length() / 2];
        for (int index = 0; index < value.length(); index += 2) {
            int high = Character.digit(value.charAt(index), 16);
            int low = Character.digit(value.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex value");
            }
            bytes[index / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }
}
