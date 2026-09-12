package com.booking.engine.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.service.StripePaymentIntentDetails;
import com.booking.engine.service.payment.BookingPaymentConstants;
import com.booking.engine.service.payment.BookingStripeMetadataKeys;
import com.booking.engine.service.payment.StripePaymentIntentVerifier;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StripePaymentIntentVerifierTest {

    private StripePaymentIntentVerifier verifier;

    @BeforeEach
    void setUp() {
        StripeProperties stripeProperties = new StripeProperties();
        stripeProperties.setCurrency("eur");
        verifier = new StripePaymentIntentVerifier(stripeProperties);
    }

    @Test
    void requireSuccessfulForBookingShouldAcceptMatchingPaymentIntent() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = booking(bookingId, new BigDecimal("35.00"));

        StripePaymentIntentDetails paymentIntent = new StripePaymentIntentDetails(
                "pi_valid",
                BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED,
                3500L,
                "EUR",
                Map.of(BookingStripeMetadataKeys.BOOKING_ID, bookingId.toString()));

        verifier.requireSuccessfulForBooking(booking, paymentIntent, BookingPaymentConstants.CONFIRM_ENDPOINT_SOURCE);
    }

    @Test
    void requireSuccessfulForBookingShouldRejectMismatchedAmount() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = booking(bookingId, new BigDecimal("35.00"));

        StripePaymentIntentDetails paymentIntent = new StripePaymentIntentDetails(
                "pi_invalid",
                BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED,
                3400L,
                "EUR",
                Map.of(BookingStripeMetadataKeys.BOOKING_ID, bookingId.toString()));

        assertThatThrownBy(() -> verifier.requireSuccessfulForBooking(
                booking,
                paymentIntent,
                BookingPaymentConstants.CONFIRM_ENDPOINT_SOURCE))
                .isInstanceOf(BookingValidationException.class);
    }

    @Test
    void isSuccessfulForAlreadyPaidBookingShouldIgnoreOriginalSlotHoldMetadata() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = booking(bookingId, new BigDecimal("35.00"));

        StripePaymentIntentDetails paymentIntent = new StripePaymentIntentDetails(
                "pi_duplicate",
                BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED,
                3500L,
                "eur",
                Map.of(BookingStripeMetadataKeys.SLOT_HOLD_ID, UUID.randomUUID().toString()));

        assertThat(verifier.isSuccessfulForAlreadyPaidBooking(
                booking,
                paymentIntent,
                BookingPaymentConstants.WEBHOOK_SOURCE)).isTrue();
    }

    private BookingEntity booking(UUID bookingId, BigDecimal amount) {
        BookingEntity booking = new BookingEntity();
        booking.setId(bookingId);
        booking.setHoldAmount(amount);
        return booking;
    }
}