package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.exception.PaymentProcessingException;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.StripePaymentService;
import com.booking.engine.service.payment.BookingPaymentConstants;
import com.booking.engine.service.StripePaymentConfirmationResult;
import com.booking.engine.service.StripePaymentIntentDetails;
import com.booking.engine.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link StripePaymentService}.
 * Provides stripe payment related business operations.
 */
@Service
@RequiredArgsConstructor
public class StripePaymentServiceImpl implements StripePaymentService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(StripePaymentServiceImpl.class);

    // ---------------------- Constants ----------------------

    private static final String PAYMENT_FAILURE_MESSAGE = "Payment could not be completed. Please try again or use a different payment method.";
    private static final String PAYMENT_STATUS_UNAVAILABLE_MESSAGE = "Unable to verify payment status right now. Please try again.";

    // ---------------------- Properties ----------------------

    private final StripeProperties stripeProperties;

    // ---------------------- Services ----------------------

    private final StripeClient stripeClient;

    private final SecurityAuditLogger securityAuditLogger;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public StripePaymentConfirmationResult createAndConfirmPaymentWithConfirmationTokenDetails(
            BigDecimal amount,
            String customerEmail,
            String confirmationTokenId,
            Map<String, String> metadata) {
        try {
            log.debug(
                    "event=payment_create_requested checkoutConfirmationPresent={} amount={} customerEmailMask={} metadataKeys={}",
                    confirmationTokenId != null && !confirmationTokenId.isBlank(),
                    amount,
                    securityAuditLogger.maskEmail(customerEmail),
                    summarizeMetadataKeys(metadata));
            PaymentIntentCreateParams params = cardPaymentIntentParams(amount, customerEmail, metadata)
                    .setConfirm(true)
                    .setConfirmationToken(confirmationTokenId)
                    .build();
            PaymentIntent intent = stripeClient.createPaymentIntent(params);
            validateCreatedPaymentIntentMatchesRequest(intent, amount, metadata, "checkout_confirmation");

            log.info(
                    "event=payment_intent_created paymentIntentHash={} amount={} currency={} paymentStatus={}",
                    hashPaymentIntentForLogs(intent.getId()),
                    amount,
                    stripeProperties.getCurrency(),
                    intent.getStatus());

            BookingCheckoutSessionResponseDto checkoutSession = BookingCheckoutSessionResponseDto.builder()
                    .paymentIntentId(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .paymentStatus(intent.getStatus())
                    .build();
            return new StripePaymentConfirmationResult(checkoutSession, toPaymentIntentDetails(intent));
        } catch (StripeException ex) {
            String details = sanitizeLogDetails(ex.getStripeError() != null && ex.getStripeError().getMessage() != null
                    ? ex.getStripeError().getMessage()
                    : ex.getMessage());
            log.warn("event=payment_create_failed flow=checkout_confirmation reason={}",
                    ex.getClass().getSimpleName());
            throw new PaymentProcessingException(
                    PAYMENT_FAILURE_MESSAGE,
                    "Stripe payment via confirmation token failed: " + details,
                    ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String createAndConfirmPayment(
            BigDecimal amount,
            String customerEmail,
            String paymentMethodId,
            Map<String, String> metadata) {
        try {
            PaymentIntentCreateParams params = cardPaymentIntentParams(amount, customerEmail, metadata)
                    .setConfirm(true)
                    .setPaymentMethod(paymentMethodId)
                    .build();

            PaymentIntent intent = stripeClient.createPaymentIntent(params);
            validateCreatedPaymentIntentMatchesRequest(intent, amount, metadata, "direct");

            if (!BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(intent.getStatus())) {
                log.warn(
                        "event=payment_create_unexpected_status paymentIntentHash={} paymentStatus={}",
                        hashPaymentIntentForLogs(intent.getId()),
                        intent.getStatus());
                throw new PaymentProcessingException(PAYMENT_FAILURE_MESSAGE);
            }

            log.info("event=payment_succeeded paymentIntentHash={} amount={} currency={}",
                    hashPaymentIntentForLogs(intent.getId()), amount, stripeProperties.getCurrency());

            return intent.getId();
        } catch (StripeException ex) {
            String details = sanitizeLogDetails(ex.getStripeError() != null && ex.getStripeError().getMessage() != null
                    ? ex.getStripeError().getMessage()
                    : ex.getMessage());
            log.warn("event=payment_create_failed flow=direct reason={}",
                    ex.getClass().getSimpleName());
            throw new PaymentProcessingException(
                    PAYMENT_FAILURE_MESSAGE,
                    "Stripe payment request failed: " + details,
                    ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StripePaymentIntentDetails getPaymentIntentDetails(String paymentIntentId) {
        try {
            PaymentIntent intent = stripeClient.retrievePaymentIntent(paymentIntentId);
            return toPaymentIntentDetails(intent);
        } catch (StripeException ex) {
            String details = sanitizeLogDetails(ex.getStripeError() != null && ex.getStripeError().getMessage() != null
                    ? ex.getStripeError().getMessage()
                    : ex.getMessage());
            log.warn("event=payment_status_lookup_failed paymentIntentHash={} reason={}",
                    hashPaymentIntentForLogs(paymentIntentId),
                    ex.getClass().getSimpleName());
            throw new PaymentProcessingException(
                    PAYMENT_STATUS_UNAVAILABLE_MESSAGE,
                    "Failed to retrieve Stripe PaymentIntent status: " + details,
                    ex);
        }
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Ensures Stripe returned the same non-secret payment facts that this server
     * requested before any caller can trust a successful confirmation.
     */
    private void validateCreatedPaymentIntentMatchesRequest(
            PaymentIntent intent,
            BigDecimal expectedAmount,
            Map<String, String> expectedMetadata,
            String flow) {
        StripePaymentIntentDetails details = toPaymentIntentDetails(intent);
        Map<String, String> metadata = expectedMetadata == null ? Map.of() : expectedMetadata;
        long expectedAmountMinor = toMinorCurrencyUnits(expectedAmount);
        boolean amountMatches = Long.valueOf(expectedAmountMinor).equals(details.amount());
        boolean currencyMatches = normalizeCurrency(stripeProperties.getCurrency()).equals(
                normalizeCurrency(details.currency()));
        boolean metadataMatches = details.metadata().entrySet().containsAll(metadata.entrySet());

        if (amountMatches && currencyMatches && metadataMatches) {
            return;
        }

        log.warn(
                "event=payment_intent_verification_failed flow={} reason={} paymentIntentHash={} expectedAmount={} actualAmount={} expectedCurrency={} actualCurrency={} metadataKeys={}",
                flow,
                verificationFailureReason(amountMatches, currencyMatches, metadataMatches),
                hashPaymentIntentForLogs(details.paymentIntentId()),
                expectedAmountMinor,
                details.amount(),
                normalizeCurrency(stripeProperties.getCurrency()),
                normalizeCurrency(details.currency()),
                summarizeMetadataKeys(details.metadata()));
        throw new PaymentProcessingException(PAYMENT_FAILURE_MESSAGE);
    }

    /*
     * Builds the shared PaymentIntent parameter set, including amount conversion
     * to cents, currency, receipt email, description, and booking metadata.
     */
    private PaymentIntentCreateParams.Builder basePaymentIntentParams(
            BigDecimal amount,
            String customerEmail,
            Map<String, String> metadata) {
        long amountInCents = toMinorCurrencyUnits(amount);

        return PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(stripeProperties.getCurrency())
                .setReceiptEmail(customerEmail)
                .setDescription(BookingPaymentConstants.BOOKING_PAYMENT_DESCRIPTION)
                .putAllMetadata(metadata);
    }

    /*
     * Extends the common PaymentIntent builder with card-specific payment method
     * configuration used by the checkout flows in this application.
     */
    private PaymentIntentCreateParams.Builder cardPaymentIntentParams(
            BigDecimal amount,
            String customerEmail,
            Map<String, String> metadata) {
        return basePaymentIntentParams(amount, customerEmail, metadata)
                .addPaymentMethodType(BookingPaymentConstants.CARD_PAYMENT_METHOD_TYPE);
    }

    /*
     * Builds a safe summary of Stripe metadata keys for logging.
     */
    private Set<String> summarizeMetadataKeys(Map<String, String> metadata) {
        return metadata == null ? Set.of() : metadata.keySet();
    }

    /*
     * Redacts contact-like provider details when tests or future diagnostics need
     * a safe string representation.
     */
    private String sanitizeLogDetails(String details) {
        return SensitiveLogSanitizer.sanitizeForLogs(details);
    }

    /*
     * Converts a Stripe SDK PaymentIntent to the internal verification snapshot.
     */
    private StripePaymentIntentDetails toPaymentIntentDetails(PaymentIntent intent) {
        return new StripePaymentIntentDetails(
                intent.getId(),
                intent.getStatus(),
                intent.getAmount(),
                intent.getCurrency(),
                intent.getMetadata());
    }

    /*
     * Converts the configured major currency amount to Stripe minor units.
     */
    private long toMinorCurrencyUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    /*
     * Normalizes currency values for case-insensitive Stripe comparisons.
     */
    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toLowerCase(Locale.ROOT);
    }

    /*
     * Builds a compact, non-sensitive reason list for payment mismatches.
     */
    private String verificationFailureReason(
            boolean amountMatches,
            boolean currencyMatches,
            boolean metadataMatches) {
        if (!amountMatches) {
            return "amount_mismatch";
        }
        if (!currencyMatches) {
            return "currency_mismatch";
        }
        if (!metadataMatches) {
            return "metadata_mismatch";
        }
        return "unknown";
    }

    /*
     * Hashes a Stripe PaymentIntent identifier for operational logs.
     */
    private String hashPaymentIntentForLogs(String paymentIntentId) {
        return SensitiveLogSanitizer.hashValue(paymentIntentId);
    }
}
