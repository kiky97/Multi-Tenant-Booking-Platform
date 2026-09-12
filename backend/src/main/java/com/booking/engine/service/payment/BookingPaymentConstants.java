package com.booking.engine.service.payment;

/**
 * Stripe payment status and source constants shared by payment services.
 */
public final class BookingPaymentConstants {

    public static final String STRIPE_STATUS_SUCCEEDED = "succeeded";
    public static final String DIRECT_CREATE_SOURCE = "direct_create";
    public static final String CHECKOUT_PREPARATION_SOURCE = "checkout_preparation";
    public static final String CONFIRM_ENDPOINT_SOURCE = "confirm_endpoint";
    public static final String WEBHOOK_SOURCE = "webhook";
    public static final String CARD_PAYMENT_METHOD_TYPE = "card";
    public static final String BOOKING_PAYMENT_DESCRIPTION = "Booking payment";

    private BookingPaymentConstants() {
    }
}
