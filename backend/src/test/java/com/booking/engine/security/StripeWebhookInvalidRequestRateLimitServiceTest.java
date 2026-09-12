package com.booking.engine.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.StripeProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StripeWebhookInvalidRequestRateLimitServiceTest {

    @Test
    void registerInvalidAttemptShouldAllowRequestsWithinLimit() {
        StripeProperties properties = stripeProperties();
        properties.getWebhook().getInvalidRateLimit().setMaxAttempts(2);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        StripeWebhookInvalidRequestRateLimitService limiter =
                new StripeWebhookInvalidRequestRateLimitService(properties, clock);

        limiter.registerInvalidAttempt("203.0.113.10", "missing_signature");

        assertDoesNotThrow(() -> limiter.registerInvalidAttempt("203.0.113.10", "invalid_signature"));
    }

    @Test
    void registerInvalidAttemptShouldRejectRequestsOverIpLimitWithSafeMessage() {
        StripeProperties properties = stripeProperties();
        properties.getWebhook().getInvalidRateLimit().setMaxAttempts(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        StripeWebhookInvalidRequestRateLimitService limiter =
                new StripeWebhookInvalidRequestRateLimitService(properties, clock);

        limiter.registerInvalidAttempt("203.0.113.10", "missing_signature");

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> limiter.registerInvalidAttempt("203.0.113.10", "invalid_signature"));

        assertEquals("Too many invalid webhook requests. Please wait a moment and try again.",
                exception.getClientMessage());
    }

    @Test
    void registerInvalidAttemptShouldAllowAgainAfterWindowAndCleanupExpiredEntries() {
        StripeProperties properties = stripeProperties();
        properties.getWebhook().getInvalidRateLimit().setMaxAttempts(1);
        properties.getWebhook().getInvalidRateLimit().setWindowSeconds(10);
        properties.getWebhook().setInvalidRateLimitCleanupIntervalSeconds(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        StripeWebhookInvalidRequestRateLimitService limiter =
                new StripeWebhookInvalidRequestRateLimitService(properties, clock);

        limiter.registerInvalidAttempt("203.0.113.10", "missing_signature");
        assertEquals(1, limiter.trackedWindowCount());

        clock.advance(Duration.ofSeconds(11));

        assertDoesNotThrow(() -> limiter.registerInvalidAttempt("203.0.113.10", "invalid_signature"));
        assertEquals(1, limiter.trackedWindowCount());
    }

    private StripeProperties stripeProperties() {
        StripeProperties properties = new StripeProperties();
        properties.setSecretKey("sk_test");
        properties.setWebhookSecret("whsec_test");
        properties.setCurrency("eur");
        return properties;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

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

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
