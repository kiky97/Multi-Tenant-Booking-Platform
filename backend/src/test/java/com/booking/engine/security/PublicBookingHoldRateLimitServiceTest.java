package com.booking.engine.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.BookingProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PublicBookingHoldRateLimitServiceTest {

    @Test
    void registerAttemptShouldLimitIpAttemptsWithinWindow() {
        BookingProperties properties = bookingProperties();
        properties.getPublicHoldRateLimit().getIp().setMaxAttempts(2);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        PublicBookingHoldRateLimitService limiter = new PublicBookingHoldRateLimitService(properties, clock);

        limiter.registerAttempt("203.0.113.10", "device-1");
        limiter.registerAttempt("203.0.113.10", "device-2");

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> limiter.registerAttempt("203.0.113.10", "device-3"));

        assertEquals("Too many appointment hold attempts. Please wait a moment and try again.",
                exception.getClientMessage());
    }

    @Test
    void registerAttemptShouldLimitDeviceAttemptsWithinWindow() {
        BookingProperties properties = bookingProperties();
        properties.getPublicHoldRateLimit().getIp().setMaxAttempts(10);
        properties.getPublicHoldRateLimit().getDevice().setMaxAttempts(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        PublicBookingHoldRateLimitService limiter = new PublicBookingHoldRateLimitService(properties, clock);

        limiter.registerAttempt("203.0.113.10", "device-1");

        assertThrows(
                RateLimitExceededException.class,
                () -> limiter.registerAttempt("203.0.113.11", "device-1"));
    }

    @Test
    void registerAttemptShouldAllowAgainAfterWindowAndCleanupExpiredEntries() {
        BookingProperties properties = bookingProperties();
        properties.getPublicHoldRateLimit().getIp().setMaxAttempts(1);
        properties.getPublicHoldRateLimit().getIp().setWindowSeconds(10);
        properties.getPublicHoldRateLimit().getDevice().setMaxAttempts(1);
        properties.getPublicHoldRateLimit().getDevice().setWindowSeconds(10);
        properties.getPublicHoldRateLimit().setCleanupIntervalSeconds(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-05-30T10:00:00Z"));
        PublicBookingHoldRateLimitService limiter = new PublicBookingHoldRateLimitService(properties, clock);

        limiter.registerAttempt("203.0.113.10", "device-1");
        assertEquals(2, limiter.trackedWindowCount());

        clock.advance(Duration.ofSeconds(11));

        assertDoesNotThrow(() -> limiter.registerAttempt("203.0.113.11", "device-2"));
        assertEquals(2, limiter.trackedWindowCount());
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
