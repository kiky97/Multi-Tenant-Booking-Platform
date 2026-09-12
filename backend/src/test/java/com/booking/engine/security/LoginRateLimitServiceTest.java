package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.AuthSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LoginRateLimitServiceTest {

    @Test
    void registerAttemptRejectsRequestsThatExceedIpLimitWithinWindow() {
        LoginRateLimitService service = service(rateLimitProperties(2, 60, 5, 60, 300), new MutableClock());

        service.registerAttempt("127.0.0.1", "admin");
        service.registerAttempt("127.0.0.1", "admin");

        assertThatThrownBy(() -> service.registerAttempt("127.0.0.1", "admin"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many login attempts. Please try again later.");
    }

    @Test
    void registerAttemptRejectsRequestsThatExceedUsernameIpLimitWithinWindow() {
        LoginRateLimitService service = service(rateLimitProperties(5, 60, 1, 60, 300), new MutableClock());

        service.registerAttempt("127.0.0.1", "admin");

        assertThatThrownBy(() -> service.registerAttempt("127.0.0.1", "admin"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many login attempts. Please try again later.");
    }

    @Test
    void oldEntriesExpireAndAreCleanedOnConfiguredInterval() {
        MutableClock clock = new MutableClock();
        LoginRateLimitService service = service(rateLimitProperties(5, 1, 5, 1, 1), clock);

        service.registerAttempt("192.0.2.10", "admin-a");
        service.registerAttempt("192.0.2.11", "admin-b");
        assertThat(service.trackedWindowCount()).isEqualTo(4);

        clock.advance(Duration.ofSeconds(2));
        service.registerAttempt("192.0.2.12", "admin-c");

        assertThat(service.trackedWindowCount()).isEqualTo(2);
    }

    @Test
    void attemptsOutsideWindowAreAllowed() {
        MutableClock clock = new MutableClock();
        LoginRateLimitService service = service(rateLimitProperties(1, 1, 5, 60, 300), clock);

        service.registerAttempt("127.0.0.1", "admin");
        clock.advance(Duration.ofSeconds(2));

        assertThatCode(() -> service.registerAttempt("127.0.0.1", "other-admin"))
                .doesNotThrowAnyException();
    }

    @Test
    void resetSuccessfulAttemptClearsOnlyUsernameIpBucket() {
        LoginRateLimitService service = service(rateLimitProperties(2, 60, 1, 60, 300), new MutableClock());

        service.registerAttempt("127.0.0.1", "admin");
        service.resetSuccessfulAttempt("127.0.0.1", "admin");

        assertThatCode(() -> service.registerAttempt("127.0.0.1", "admin"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.registerAttempt("127.0.0.1", "other-admin"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many login attempts. Please try again later.");
    }

    private LoginRateLimitService service(AuthSecurityProperties properties, Clock clock) {
        SecurityAuditLogger securityAuditLogger = Mockito.mock(SecurityAuditLogger.class);
        Mockito.when(securityAuditLogger.hashValue(Mockito.anyString())).thenReturn("fingerprint");
        return new LoginRateLimitService(properties, securityAuditLogger, clock);
    }

    private AuthSecurityProperties rateLimitProperties(
            int ipMaxAttempts,
            long ipWindowSeconds,
            int usernameIpMaxAttempts,
            long usernameIpWindowSeconds,
            long cleanupIntervalSeconds) {

        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getRateLimit().getIp().setMaxAttempts(ipMaxAttempts);
        properties.getRateLimit().getIp().setWindowSeconds(ipWindowSeconds);
        properties.getRateLimit().getUsernameIp().setMaxAttempts(usernameIpMaxAttempts);
        properties.getRateLimit().getUsernameIp().setWindowSeconds(usernameIpWindowSeconds);
        properties.getRateLimit().setCleanupIntervalSeconds(cleanupIntervalSeconds);
        return properties;
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-05-30T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
