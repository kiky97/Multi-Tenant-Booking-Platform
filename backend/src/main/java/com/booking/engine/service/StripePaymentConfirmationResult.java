package com.booking.engine.service;

import com.booking.engine.dto.BookingCheckoutSessionResponseDto;

/**
 * Internal result for a server-side PaymentIntent confirmation.
 */
public record StripePaymentConfirmationResult(
        BookingCheckoutSessionResponseDto checkoutSession,
        StripePaymentIntentDetails paymentIntent) {
}
