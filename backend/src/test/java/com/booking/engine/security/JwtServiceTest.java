package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.engine.properties.JwtProperties;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {

    @Test
    void generateTokenAndValidateWorksWithRawSecretFallback() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("raw-secret-for-tests-12345678901234567890!");
        properties.setExpirationSeconds(3600);
        properties.setIssuer("salon-booking-platform");
        JwtService jwtService = new JwtService(properties);

        UserDetails userDetails = User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        String token = jwtService.generateToken(userDetails, Map.of("scope", "admin"));

        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void generateTokenAndValidateWorksWithBase64Secret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(
                "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)));
        properties.setExpirationSeconds(3600);
        properties.setIssuer("salon-booking-platform");
        JwtService jwtService = new JwtService(properties);

        UserDetails userDetails = User.withUsername("owner")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        String token = jwtService.generateToken(userDetails, Map.of());

        assertThat(jwtService.extractUsername(token)).isEqualTo("owner");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void generateTokenWithVersionClaimValidatesAgainstExpectedTokenVersion() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("raw-secret-for-tests-12345678901234567890!");
        properties.setExpirationSeconds(3600);
        properties.setIssuer("salon-booking-platform");
        JwtService jwtService = new JwtService(properties);

        UserDetails userDetails = User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        String token = jwtService.generateToken(userDetails, Map.of(), 7);

        assertThat(jwtService.extractTokenVersion(token)).isEqualTo(7);
        assertThat(jwtService.isTokenValid(token, userDetails, 7)).isTrue();
        assertThat(jwtService.isTokenValid(token, userDetails, 8)).isFalse();
    }

    @Test
    void generateCsrfTokenCreatesExtractableSignedClaim() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("raw-secret-for-tests-12345678901234567890!");
        properties.setExpirationSeconds(3600);
        properties.setIssuer("salon-booking-platform");
        JwtService jwtService = new JwtService(properties);

        UserDetails userDetails = User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        String csrfToken = jwtService.generateCsrfToken();
        String token = jwtService.generateToken(userDetails, Map.of(JwtService.CSRF_TOKEN_CLAIM, csrfToken), 7);

        assertThat(csrfToken).hasSizeGreaterThanOrEqualTo(32);
        assertThat(jwtService.extractCsrfToken(token)).isEqualTo(csrfToken);
    }

    @Test
    void tokenExtractionFailsWhenExpired() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("another-raw-secret-for-tests-123456789012345!");
        properties.setExpirationSeconds(-5);
        properties.setIssuer("salon-booking-platform");
        JwtService jwtService = new JwtService(properties);

        UserDetails userDetails = User.withUsername("expired-user")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        String token = jwtService.generateToken(userDetails, Map.of());

        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenValidationRejectsUnexpectedIssuer() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("raw-secret-for-tests-12345678901234567890!");
        properties.setExpirationSeconds(3600);
        properties.setIssuer("salon-booking-platform");
        JwtService jwtService = new JwtService(properties);

        UserDetails userDetails = User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        String token = Jwts.builder()
                .subject("admin")
                .issuer("different-issuer")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails))
                .isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void constructorFailsFastWhenJwtSecretIsTooWeak() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("short-secret");
        properties.setExpirationSeconds(3600);
        properties.setIssuer("salon-booking-platform");

        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid JWT secret configuration");
    }

    @Test
    void constructorFailsFastWhenJwtSecretIsPlaceholder() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("<generate-a-long-random-secret>");
        properties.setExpirationSeconds(3600);
        properties.setIssuer("salon-booking-platform");

        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an empty value or placeholder");
    }
}
