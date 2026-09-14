package com.booking.engine.platform.service;

import com.booking.engine.entity.Booking;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.platform.config.StripeProperties;
import com.booking.engine.platform.repository.BookingRepository;
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
    private final StripeProperties stripeProperties;

    public StripePaymentSyncService(BookingRepository bookings, StripeProperties stripeProperties) {
        this.bookings = bookings;
        this.stripeProperties = stripeProperties;
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
        } else if (StripePaymentIntentEventTypes.CANCELED.equals(eventType)
                || StripePaymentIntentEventTypes.PAYMENT_FAILED.equals(eventType)) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookings.save(booking);
            log.info("event=stripe_webhook_applied bookingId={} eventType={} status=CANCELLED", booking.getId(), eventType);
        }
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
