package com.booking.engine.stripe;

/**
 * Stripe PaymentIntent event type names supported by webhook processing.
 */
public final class StripePaymentIntentEventTypes {

    public static final String SUCCEEDED = "payment_intent.succeeded";
    public static final String CANCELED = "payment_intent.canceled";
    public static final String PAYMENT_FAILED = "payment_intent.payment_failed";

    private StripePaymentIntentEventTypes() {
    }
}
