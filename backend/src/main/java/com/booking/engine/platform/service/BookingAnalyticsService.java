package com.booking.engine.platform.service;

import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis-backed counters fed by the Kafka analytics consumer, independent of the primary
 * Postgres booking data so a slow/late consumer never blocks the booking or payment path. */
@Service
public class BookingAnalyticsService {
    private static final String KEY_PREFIX = "analytics:confirmed-bookings:";

    private final StringRedisTemplate redis;

    public BookingAnalyticsService(StringRedisTemplate redis) { this.redis = redis; }

    public void recordConfirmedBooking(UUID organizationId) {
        redis.opsForValue().increment(KEY_PREFIX + organizationId);
    }

    public long confirmedBookingCount(UUID organizationId) {
        String value = redis.opsForValue().get(KEY_PREFIX + organizationId);
        return value == null ? 0L : Long.parseLong(value);
    }
}
