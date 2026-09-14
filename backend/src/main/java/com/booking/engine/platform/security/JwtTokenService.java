package com.booking.engine.platform.security;

import com.booking.engine.entity.User;
import com.booking.engine.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final SecretKey key;
    private final long expirationSeconds;
    private final String issuer;

    public JwtTokenService(@Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds:1800}") long expirationSeconds,
            @Value("${app.jwt.issuer:multi-tenant-booking-platform}") String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
    }

    public String create(User user) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(issuer).subject(user.getId().toString()).claim("role", user.getRole().name())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expirationSeconds))).signWith(key).compact();
    }

    public PlatformPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token).getPayload();
        return new PlatformPrincipal(UUID.fromString(claims.getSubject()), UserRole.valueOf(claims.get("role", String.class)));
    }
}
