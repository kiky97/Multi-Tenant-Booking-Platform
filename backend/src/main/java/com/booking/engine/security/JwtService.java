package com.booking.engine.security;

import com.booking.engine.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Service for JWT generation and validation.
 */
@Component
public final class JwtService {

    public static final String TOKEN_VERSION_CLAIM = "ver";
    public static final String CSRF_TOKEN_CLAIM = "csrf";
    private static final int CSRF_TOKEN_BYTES = 32;

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = buildSigningKey(jwtProperties.getSecret());
    }

    public String generateToken(UserDetails userDetails, Map<String, Object> claims) {
        return buildToken(userDetails, claims);
    }

    public String generateToken(UserDetails userDetails, Map<String, Object> claims, int tokenVersion) {
        Map<String, Object> tokenClaims = new LinkedHashMap<>(claims);
        tokenClaims.put(TOKEN_VERSION_CLAIM, tokenVersion);
        return buildToken(userDetails, tokenClaims);
    }

    public Integer extractTokenVersion(String token) {
        return extractClaims(token).get(TOKEN_VERSION_CLAIM, Integer.class);
    }

    public String generateCsrfToken() {
        byte[] bytes = new byte[CSRF_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String extractCsrfToken(String token) {
        return extractClaims(token).get(CSRF_TOKEN_CLAIM, String.class);
    }

    public boolean isTokenValid(String token, UserDetails userDetails, int expectedTokenVersion) {
        Claims claims = extractClaims(token);
        String username = claims.getSubject();
        Date expiration = claims.getExpiration();
        Integer tokenVersion = claims.get(TOKEN_VERSION_CLAIM, Integer.class);

        return username != null
                && username.equals(userDetails.getUsername())
                && expiration != null
                && expiration.after(new Date())
                && tokenVersion != null
                && tokenVersion == expectedTokenVersion;
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractClaims(token);
        String username = claims.getSubject();
        Date expiration = claims.getExpiration();

        return username != null
                && username.equals(userDetails.getUsername())
                && expiration != null
                && expiration.after(new Date());
    }

    private String buildToken(UserDetails userDetails, Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getExpirationSeconds());

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey buildSigningKey(String secret) {
        try {
            validateSecretValue(secret);
            byte[] keyBytes;
            try {
                keyBytes = Decoders.BASE64.decode(secret);
            } catch (Exception ignored) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (WeakKeyException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid JWT secret configuration. Configure at least 256 bits after Base64 decoding or UTF-8 encoding.",
                    ex);
        }
    }

    private void validateSecretValue(String secret) {
        if (secret == null || secret.isBlank() || secret.contains("${") || secret.trim().matches("<[^>]+>")) {
            throw new IllegalStateException(
                    "Invalid JWT secret configuration. Configure a real random secret, not an empty value or placeholder.");
        }
    }
}
