package com.booking.engine.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.Booking;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.Payment;
import com.booking.engine.entity.PaymentStatus;
import com.booking.engine.platform.config.StripeProperties;
import com.booking.engine.platform.kafka.BookingEventPublisher;
import com.booking.engine.platform.repository.BookingRepository;
import com.booking.engine.platform.repository.PaymentRepository;
import com.booking.engine.platform.stripe.StripePaymentIntentEventTypes;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The webhook is the only place a booking is allowed to become CONFIRMED, so these tests focus on
 * the guarantees that make that trustworthy: amount/currency verification and idempotency against
 * Stripe's at-least-once webhook delivery. They also cover the payments ledger, which records
 * every applied event separately from the booking's own current status.
 */
class StripePaymentSyncServiceTest {

    private BookingRepository bookings;
    private PaymentRepository payments;
    private BookingEventPublisher events;
    private StripePaymentSyncService syncService;
    private Booking booking;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepository.class);
        payments = mock(PaymentRepository.class);
        events = mock(BookingEventPublisher.class);
        StripeProperties properties = new StripeProperties("sk_test", "whsec_test", "eur", "pk_test", 1_048_576);
        syncService = new StripePaymentSyncService(bookings, payments, properties, events);

        booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.HELD);
        booking.setAmount(new BigDecimal("45.50"));
        booking.setStripePaymentIntentId("pi_123");

        when(bookings.findByStripePaymentIntentId("pi_123")).thenReturn(Optional.of(booking));
        when(bookings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void succeededEventConfirmsBookingWhenAmountAndCurrencyMatch() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.SUCCEEDED, 4550L, "eur");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookings).save(booking);
        // Downstream email/analytics fan-out only fires once the booking is genuinely confirmed.
        verify(events).publishConfirmed(booking, "eur");

        Payment recorded = capturePayment();
        assertThat(recorded.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(recorded.getStripePaymentIntentId()).isEqualTo("pi_123");
        assertThat(recorded.getAmount()).isEqualByComparingTo(new BigDecimal("45.50"));
        assertThat(recorded.getCurrency()).isEqualTo("eur");
    }

    @Test
    void succeededEventIsRejectedWhenAmountDoesNotMatch() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.SUCCEEDED, 100L, "eur");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        verify(bookings, never()).save(any());
        verify(events, never()).publishConfirmed(any(), any());
        verify(payments, never()).save(any());
    }

    @Test
    void succeededEventIsRejectedWhenCurrencyDoesNotMatch() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.SUCCEEDED, 4550L, "usd");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        verify(bookings, never()).save(any());
        verify(events, never()).publishConfirmed(any(), any());
        verify(payments, never()).save(any());
    }

    @Test
    void canceledEventCancelsAPendingBookingAndRecordsACanceledPayment() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.CANCELED, null, null);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(events, never()).publishConfirmed(any(), any());
        assertThat(capturePayment().getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void paymentFailedEventCancelsAPendingBookingAndRecordsAFailedPayment() {
        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.PAYMENT_FAILED, null, null);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(events, never()).publishConfirmed(any(), any());
        assertThat(capturePayment().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void duplicateSucceededEventOnAnAlreadyConfirmedBookingIsIgnored() {
        booking.setStatus(BookingStatus.CONFIRMED);

        syncService.handlePaymentIntentEvent("pi_123", StripePaymentIntentEventTypes.SUCCEEDED, 4550L, "eur");

        verify(bookings, never()).save(any());
        // Idempotency matters here specifically because a duplicate event must not send a second
        // confirmation email, double-count the booking in analytics, or add a second ledger row.
        verify(events, never()).publishConfirmed(any(), any());
        verify(payments, never()).save(any());
    }

    @Test
    void unknownPaymentIntentIsIgnoredWithoutError() {
        when(bookings.findByStripePaymentIntentId("pi_unknown")).thenReturn(Optional.empty());

        syncService.handlePaymentIntentEvent("pi_unknown", StripePaymentIntentEventTypes.SUCCEEDED, 4550L, "eur");

        verify(bookings, never()).save(any());
        verify(events, never()).publishConfirmed(any(), any());
        verify(payments, never()).save(any());
    }

    private Payment capturePayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());
        return captor.getValue();
    }
}
