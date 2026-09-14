package com.booking.engine.platform.service;

import com.booking.engine.entity.Booking;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.Payment;
import com.booking.engine.entity.PaymentStatus;
import com.booking.engine.platform.config.StripeProperties;
import com.booking.engine.platform.kafka.BookingEventPublisher;
import com.booking.engine.platform.repository.BookingRepository;
import com.booking.engine.platform.repository.PaymentRepository;
import com.booking.engine.platform.stripe.StripePaymentIntentEventTypes;
import jakarta.transaction.Transactional;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Applies Stripe webhook events to bookings. This is the only place a booking is allowed to move
 * from HELD to CONFIRMED — never the booking-creation request itself — so a client can never
 * talk its way into a "paid" booking without Stripe actually saying so.
 */
@Service
public class StripePaymentSyncService {
    private static final Logger log = LoggerFactory.getLogger(StripePaymentSyncService.class);

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final StripeProperties stripeProperties;
    private final BookingEventPublisher events;

    public StripePaymentSyncService(BookingRepository bookings, PaymentRepository payments,
            StripeProperties stripeProperties, BookingEventPublisher events) {
        this.bookings = bookings;
        this.payments = payments;
        this.stripeProperties = stripeProperties;
        this.events = events;
    }

    @Transactional
    public void handlePaymentIntentEvent(String paymentIntentId, String eventType, Long amountMinor, String currency) {
        Booking booking = bookings.findByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (booking == null) {
            log.warn("event=stripe_webhook_unmatched paymentIntentHash={} eventType={}", hash(paymentIntentId), eventType);
            return;
        }

        // Stripe retries webhook delivery, so a booking already resolved (paid or cancelled) must
        // not be re-processed by a duplicate event.
        if (booking.getStatus() != BookingStatus.HELD) {
            return;
        }

        if (StripePaymentIntentEventTypes.SUCCEEDED.equals(eventType)) {
            if (!amountAndCurrencyMatch(booking, amountMinor, currency)) {
                log.warn("event=stripe_webhook_amount_mismatch bookingId={} paymentIntentHash={} expectedAmount={} actualAmount={} expectedCurrency={} actualCurrency={}",
                        booking.getId(), hash(paymentIntentId), toMinorCurrencyUnits(booking.getAmount()), amountMinor,
                        normalize(stripeProperties.getCurrency()), normalize(currency));
                return;
            }
            booking.setStatus(BookingStatus.CONFIRMED);
            bookings.save(booking);
            log.info("event=stripe_webhook_applied bookingId={} eventType={} status=CONFIRMED", booking.getId(), eventType);
            recordPayment(booking, paymentIntentId, currency, PaymentStatus.SUCCEEDED);
            events.publishConfirmed(booking, stripeProperties.getCurrency());
        } else if (StripePaymentIntentEventTypes.CANCELED.equals(eventType)
                || StripePaymentIntentEventTypes.PAYMENT_FAILED.equals(eventType)) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookings.save(booking);
            log.info("event=stripe_webhook_applied bookingId={} eventType={} status=CANCELLED", booking.getId(), eventType);
            PaymentStatus paymentStatus = StripePaymentIntentEventTypes.CANCELED.equals(eventType)
                    ? PaymentStatus.CANCELED
                    : PaymentStatus.FAILED;
            recordPayment(booking, paymentIntentId, currency, paymentStatus);
        }
    }

    /** Records the event that was just applied. The ledger uses the booking's own amount (the
     * figure both sides already agreed on) rather than re-deriving it from Stripe's minor-unit
     * amount, since a failed/canceled event may not carry a verified amount at all. */
    private void recordPayment(Booking booking, String paymentIntentId, String currency, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setAmount(booking.getAmount());
        payment.setCurrency(currency != null ? currency : stripeProperties.getCurrency());
        payment.setStatus(status);
        payments.save(payment);
    }

    private boolean amountAndCurrencyMatch(Booking booking, Long amountMinor, String currency) {
        Long expectedAmountMinor = toMinorCurrencyUnits(booking.getAmount());
        boolean amountMatches = expectedAmountMinor != null && expectedAmountMinor.equals(amountMinor);
        boolean currencyMatches = normalize(stripeProperties.getCurrency()).equals(normalize(currency));
        return amountMatches && currencyMatches;
    }

    private static Long toMinorCurrencyUnits(java.math.BigDecimal amount) {
        return amount == null ? null : amount.movePointRight(2).longValueExact();
    }

    private static String normalize(String currency) {
        return currency == null ? "" : currency.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String value) {
        return value == null ? "none" : Integer.toHexString(value.hashCode());
    }
}
