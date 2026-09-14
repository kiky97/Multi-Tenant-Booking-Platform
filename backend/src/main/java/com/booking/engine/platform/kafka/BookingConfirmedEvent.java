package com.booking.engine.platform.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published once a booking's Stripe payment is confirmed by webhook. Consumers fan out side
 * effects (notification, analytics) that must not block the webhook's own transaction. */
public record BookingConfirmedEvent(
        UUID bookingId,
        UUID organizationId,
        UUID customerId,
        UUID staffId,
        UUID serviceId,
        Instant startTime,
        BigDecimal amount,
        String currency) {
}
