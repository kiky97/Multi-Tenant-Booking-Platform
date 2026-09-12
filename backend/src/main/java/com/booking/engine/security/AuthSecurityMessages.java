package com.booking.engine.security;

/**
 * Neutral client-facing messages for authentication-related flows.
 */
public final class AuthSecurityMessages {

    public static final String INVALID_CREDENTIALS = "Invalid username or password";
    public static final String AUTHENTICATION_FAILED = "Authentication failed";
    public static final String TOO_MANY_LOGIN_ATTEMPTS = "Too many login attempts. Please try again later.";

    private AuthSecurityMessages() {
    }
}
