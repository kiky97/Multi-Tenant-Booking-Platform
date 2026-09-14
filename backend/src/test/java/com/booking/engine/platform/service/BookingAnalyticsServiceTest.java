package com.booking.engine.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class BookingAnalyticsServiceTest {

    private StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private BookingAnalyticsService analytics;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        analytics = new BookingAnalyticsService(redis);
    }

    @Test
    void recordConfirmedBookingIncrementsTheOrganizationScopedKey() {
        analytics.recordConfirmedBooking(organizationId);

        verify(valueOps).increment("analytics:confirmed-bookings:" + organizationId);
    }

    @Test
    void confirmedBookingCountReturnsZeroWhenNothingRecordedYet() {
        when(valueOps.get("analytics:confirmed-bookings:" + organizationId)).thenReturn(null);

        assertThat(analytics.confirmedBookingCount(organizationId)).isZero();
    }

    @Test
    void confirmedBookingCountParsesTheStoredValue() {
        when(valueOps.get("analytics:confirmed-bookings:" + organizationId)).thenReturn("7");

        assertThat(analytics.confirmedBookingCount(organizationId)).isEqualTo(7L);
    }
}
