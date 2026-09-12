package com.booking.engine.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.security.PublicBookingActionRateLimitService.Action;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicBookingActionRateLimitServiceTest {

    @Test
    void registerAttemptShouldAllowRequestsWithinLimit() {
        BookingProperties properties = bookingProperties();
        properties.getPublicActionRateLimit().getIp().setMaxAttempts(2);
        properties.getPublicActionRateLimit().getTarget().setMaxAttempts(2);
        properties.getPublicActionRateLimit().getDevice().setMaxAttempts(2);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        PublicBookingActionRateLimitService limiter = new PublicBookingActionRateLimitService(properties, clock);
        UUID bookingId = UUID.randomUUID();

        limiter.registerAttempt(Action.CONFIRM, "203.0.113.10", bookingId, "device-1");

        assertDoesNotThrow(() -> limiter.registerAttempt(Action.CONFIRM, "203.0.113.10", bookingId, "device-1"));
    }

    @Test
    void registerAttemptShouldRejectRequestsOverIpLimitWithSafeMessage() {
        BookingProperties properties = bookingProperties();
        properties.getPublicActionRateLimit().getIp().setMaxAttempts(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        PublicBookingActionRateLimitService limiter = new PublicBookingActionRateLimitService(properties, clock);

        limiter.registerAttempt(Action.DIRECT_CREATE, "203.0.113.10", null, "device-1");

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> limiter.registerAttempt(Action.DIRECT_CREATE, "203.0.113.10", null, "device-2"));

        assertEquals("Too many booking requests. Please wait a moment and try again.",
                exception.getClientMessage());
    }

    @Test
    void registerAttemptShouldRejectRequestsOverTargetLimitWithSafeMessage() {
        BookingProperties properties = bookingProperties();
        properties.getPublicActionRateLimit().getIp().setMaxAttempts(10);
        properties.getPublicActionRateLimit().getTarget().setMaxAttempts(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        PublicBookingActionRateLimitService limiter = new PublicBookingActionRateLimitService(properties, clock);
        UUID bookingId = UUID.randomUUID();

        limiter.registerAttempt(Action.CHECKOUT_PREPARE, "203.0.113.10", bookingId, null);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> limiter.registerAttempt(Action.CHECKOUT_PREPARE, "203.0.113.10", bookingId, null));

        assertEquals("Too many booking requests. Please wait a moment and try again.",
                exception.getClientMessage());
    }

    @Test
    void registerAttemptShouldAllowAgainAfterWindowAndCleanupExpiredEntries() {
        BookingProperties properties = bookingProperties();
        properties.getPublicActionRateLimit().getIp().setMaxAttempts(1);
        properties.getPublicActionRateLimit().getIp().setWindowSeconds(10);
        properties.getPublicActionRateLimit().getTarget().setMaxAttempts(1);
        properties.getPublicActionRateLimit().getTarget().setWindowSeconds(10);
        properties.getPublicActionRateLimit().getDevice().setMaxAttempts(1);
        properties.getPublicActionRateLimit().getDevice().setWindowSeconds(10);
        properties.getPublicActionRateLimit().setCleanupIntervalSeconds(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        PublicBookingActionRateLimitService limiter = new PublicBookingActionRateLimitService(properties, clock);
        UUID bookingId = UUID.randomUUID();

        limiter.registerAttempt(Action.CANCEL, "203.0.113.10", bookingId, "device-1");
        assertEquals(3, limiter.trackedWindowCount());

        clock.advance(Duration.ofSeconds(11));

        assertDoesNotThrow(() -> limiter.registerAttempt(Action.CANCEL, "203.0.113.10", bookingId, "device-1"));
        assertEquals(3, limiter.trackedWindowCount());
    }

    private BookingProperties bookingProperties() {
        BookingProperties properties = new BookingProperties();
        properties.setTimezone("Europe/Dublin");
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
