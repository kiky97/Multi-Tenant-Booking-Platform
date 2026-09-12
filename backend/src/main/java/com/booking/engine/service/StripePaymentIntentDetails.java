package com.booking.engine.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Internal snapshot of Stripe PaymentIntent fields required before a booking
 * can trust a successful payment.
 */
public record StripePaymentIntentDetails(
        String paymentIntentId,
        String status,
        Long amount,
        String currency,
        Map<String, String> metadata) {

    public StripePaymentIntentDetails {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(metadata));
    }
}
