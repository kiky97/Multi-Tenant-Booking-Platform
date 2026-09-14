package com.booking.engine.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.Booking;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.platform.config.StripeProperties;
import com.booking.engine.platform.repository.BookingRepository;
import com.booking.engine.platform.stripe.StripePaymentIntentEventTypes;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The webhook is the only place a booking is allowed to become CONFIRMED, so these tests focus on
 * the guarantees that make that trustworthy: amount/currency verification and idempotency against
 * Stripe's at-least-once webhook delivery.
 */
class StripePaymentSyncServiceTest {

    private BookingRepository bookings;
    private StripePaymentSyncService syncService;
    private Booking booking;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepository.class);
        StripeProperties properties = new StripeProperties("sk_test", "whsec_test", "eur", "pk_test", 1_048_576);
        syncService = new StripePaymentSyncService(bookings, properties);

        booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.HELD);
        booking.setAmount(new BigDecimal("45.50"));
        booking.setStripePaymentIntentId("pi_123");

        when(bookings.findByStripePaymentIntentId("pi_123")).thenReturn(Optional.of(booking));
        when(bookings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void succeededEventConfirmsBookingWhenAmountAndCurrencyMatch() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.SUCCEEDED, 4550L, "eur");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookings).save(booking);
    }

    @Test
    void succeededEventIsRejectedWhenAmountDoesNotMatch() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.SUCCEEDED, 100L, "eur");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        verify(bookings, never()).save(any());
    }

    @Test
    void succeededEventIsRejectedWhenCurrencyDoesNotMatch() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.SUCCEEDED, 4550L, "usd");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        verify(bookings, never()).save(any());
    }

    @Test
    void canceledEventCancelsAPendingBooking() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.CANCELED, null, null);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void paymentFailedEventCancelsAPendingBooking() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.PAYMENT_FAILED, null, null);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void duplicateSucceededEventOnAnAlreadyConfirmedBookingIsIgnored() {
        booking.setStatus(BookingStatus.CONFIRMED);

        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.SUCCEEDED, 4550L, "eur");

        verify(bookings, never()).save(any());
    }

    @Test
    void unknownPaymentIntentIsIgnoredWithoutError() {
        when(bookings.findByStripePaymentIntentId("pi_unknown")).thenReturn(Optional.empty());

        syncService.handlePaymentIntentEvent("pi_unknown", StripePaymentIntentEventTypes.SUCCEEDED, 4550L, "eur");

        verify(bookings, never()).save(any());
    }
}
