package com.booking.engine.service;

import java.util.Map;

/**
 * Service contract for booking payment sync operations.
 * Defines booking payment sync related business operations.
 */
public interface BookingPaymentSyncService {

    /**
     * Synchronizes booking payment state from a Stripe webhook.
     *
     * @param paymentIntentId Stripe PaymentIntent identifier
     * @param paymentStatus   Stripe PaymentIntent status
     * @param eventType       Stripe webhook event type
     */
    default void syncStripePaymentIntentFromWebhook(String paymentIntentId, String paymentStatus, String eventType) {
        syncStripePaymentIntentFromWebhook(paymentIntentId, paymentStatus, eventType, Map.of());
    }

    /**
     * Synchronizes booking payment state from a Stripe webhook with metadata
     * context.
     *
     * @param paymentIntentId Stripe PaymentIntent identifier
     * @param paymentStatus   Stripe PaymentIntent status
     * @param eventType       Stripe webhook event type
     * @param metadata        non-PII Stripe metadata values
     */
    default void syncStripePaymentIntentFromWebhook(
            String paymentIntentId,
            String paymentStatus,
            String eventType,
            Map<String, String> metadata) {
        syncStripePaymentIntentFromWebhook(
                new StripePaymentIntentDetails(paymentIntentId, paymentStatus, null, null, metadata),
                eventType);
    }

    /**
     * Synchronizes booking payment state from a Stripe webhook with all
     * PaymentIntent fields required for success verification.
     *
     * @param paymentIntent Stripe PaymentIntent snapshot from a verified webhook
     * @param eventType     Stripe webhook event type
     */
    void syncStripePaymentIntentFromWebhook(StripePaymentIntentDetails paymentIntent, String eventType);
}
