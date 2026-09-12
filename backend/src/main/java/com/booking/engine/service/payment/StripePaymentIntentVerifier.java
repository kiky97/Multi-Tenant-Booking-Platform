package com.booking.engine.service.payment;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.StripePaymentIntentDetails;
import com.booking.engine.validation.BookingValidationMessages;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies Stripe PaymentIntent snapshots before booking state transitions
 * trust a successful payment.
 */
@Component
@RequiredArgsConstructor
public class StripePaymentIntentVerifier {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentIntentVerifier.class);

    private final StripeProperties stripeProperties;

    public void requireSuccessfulForBooking(
            BookingEntity booking,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        if (!isSuccessfulForBooking(booking, paymentIntent, source)) {
            throw new BookingValidationException(BookingValidationMessages.STRIPE_PAYMENT_MISMATCH);
        }
    }

    public void requireSuccessfulForSlotHold(
            SlotHoldEntity slotHold,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        if (!isSuccessfulForSlotHold(slotHold, paymentIntent, source)) {
            throw new BookingValidationException(BookingValidationMessages.STRIPE_PAYMENT_MISMATCH);
        }
    }

    public boolean isSuccessfulForBooking(
            BookingEntity booking,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        return validateSuccessfulPaymentIntent(
                paymentIntent,
                booking.getHoldAmount(),
                BookingStripeMetadataKeys.BOOKING_ID,
                booking.getId(),
                "booking",
                source,
                true);
    }

    public boolean isSuccessfulForSlotHold(
            SlotHoldEntity slotHold,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        return validateSuccessfulPaymentIntent(
                paymentIntent,
                slotHold.getHoldAmount(),
                BookingStripeMetadataKeys.SLOT_HOLD_ID,
                slotHold.getId(),
                "slot_hold",
                source,
                true);
    }

    public boolean isSuccessfulForAlreadyPaidBooking(
            BookingEntity booking,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        Long expectedAmountMinor = toMinorCurrencyUnits(booking.getHoldAmount());
        String reason = null;

        if (!BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(paymentIntent.status())) {
            reason = "status_mismatch";
        } else if (expectedAmountMinor == null || paymentIntent.amount() == null
                || !expectedAmountMinor.equals(paymentIntent.amount())) {
            reason = "amount_mismatch";
        } else if (!normalizeCurrency(stripeProperties.getCurrency())
                .equals(normalizeCurrency(paymentIntent.currency()))) {
            reason = "currency_mismatch";
        }

        if (reason == null) {
            return true;
        }

        log.warn(
                "event=stripe_payment_verification_failed source={} reason={} entityType=booking entityId={} paymentIntentHash={} expectedAmount={} actualAmount={} expectedCurrency={} actualCurrency={} metadataKeys={}",
                source,
                reason,
                booking.getId(),
                hashPaymentIntentForLogs(paymentIntent.paymentIntentId()),
                expectedAmountMinor,
                paymentIntent.amount(),
                normalizeCurrency(stripeProperties.getCurrency()),
                normalizeCurrency(paymentIntent.currency()),
                paymentIntent.metadata().keySet());
        return false;
    }

    public void requirePaymentIntentIdMatches(
            UUID entityId,
            String expectedPaymentIntentId,
            StripePaymentIntentDetails paymentIntent,
            String entityType,
            String source) {
        if (expectedPaymentIntentId != null && expectedPaymentIntentId.equals(paymentIntent.paymentIntentId())) {
            return;
        }

        log.warn(
                "event=stripe_payment_verification_failed source={} reason=payment_intent_mismatch entityType={} entityId={} paymentIntentHash={} expectedPaymentIntentHash={}",
                source,
                entityType,
                entityId,
                hashPaymentIntentForLogs(paymentIntent.paymentIntentId()),
                hashPaymentIntentForLogs(expectedPaymentIntentId));

        throw new BookingValidationException(BookingValidationMessages.STRIPE_PAYMENT_MISMATCH);
    }

    private boolean validateSuccessfulPaymentIntent(
            StripePaymentIntentDetails paymentIntent,
            BigDecimal expectedAmount,
            String expectedMetadataKey,
            UUID expectedMetadataValue,
            String entityType,
            String source,
            boolean requireMetadataMatch) {
        Long expectedAmountMinor = toMinorCurrencyUnits(expectedAmount);
        String reason = null;

        if (!BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(paymentIntent.status())) {
            reason = "status_mismatch";
        } else if (expectedAmountMinor == null || paymentIntent.amount() == null
                || !expectedAmountMinor.equals(paymentIntent.amount())) {
            reason = "amount_mismatch";
        } else if (!normalizeCurrency(stripeProperties.getCurrency())
                .equals(normalizeCurrency(paymentIntent.currency()))) {
            reason = "currency_mismatch";
        } else if (requireMetadataMatch
                && !expectedMetadataValue.toString().equals(paymentIntent.metadata().get(expectedMetadataKey))) {
            reason = "metadata_mismatch";
        }

        if (reason == null) {
            return true;
        }

        log.warn(
                "event=stripe_payment_verification_failed source={} reason={} entityType={} entityId={} paymentIntentHash={} expectedAmount={} actualAmount={} expectedCurrency={} actualCurrency={} expectedMetadataKey={} metadataKeys={}",
                source,
                reason,
                entityType,
                expectedMetadataValue,
                hashPaymentIntentForLogs(paymentIntent.paymentIntentId()),
                expectedAmountMinor,
                paymentIntent.amount(),
                normalizeCurrency(stripeProperties.getCurrency()),
                normalizeCurrency(paymentIntent.currency()),
                expectedMetadataKey,
                paymentIntent.metadata().keySet());
        return false;
    }

    public boolean isAlreadySuccessfulBooking(BookingEntity booking) {
        return (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.DONE)
                && (BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(booking.getStripePaymentStatus())
                        || booking.getPaymentCapturedAt() != null);
    }

    private Long toMinorCurrencyUnits(BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toLowerCase(Locale.ROOT);
    }

    private String hashPaymentIntentForLogs(String paymentIntentId) {
        return SensitiveLogSanitizer.hashValue(paymentIntentId);
    }
}