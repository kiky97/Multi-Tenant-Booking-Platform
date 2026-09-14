package com.booking.engine.platform.service;

import com.booking.engine.platform.config.StripeProperties;
import com.booking.engine.platform.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates the Stripe PaymentIntent for a booking. Confirmation itself happens client-side (the
 * frontend uses the returned {@code clientSecret} with Stripe.js); this server never trusts a
 * client-reported "payment succeeded" — only the Stripe webhook (see
 * {@link StripePaymentSyncService}) is allowed to move a booking to CONFIRMED.
 */
@Service
public class StripePaymentService {
    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);
    private static final String BOOKING_PAYMENT_DESCRIPTION = "Booking payment";

    private final StripeClient stripeClient;
    private final StripeProperties stripeProperties;

    public StripePaymentService(StripeClient stripeClient, StripeProperties stripeProperties) {
        this.stripeClient = stripeClient;
        this.stripeProperties = stripeProperties;
    }

    public PaymentIntentSnapshot createPaymentIntentForBooking(BigDecimal amount, String customerEmail,
            Map<String, String> metadata) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toMinorCurrencyUnits(amount))
                    .setCurrency(stripeProperties.getCurrency())
                    .setReceiptEmail(customerEmail)
                    .setDescription(BOOKING_PAYMENT_DESCRIPTION)
                    .putAllMetadata(metadata)
                    .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true)
                            .build())
                    .build();
            PaymentIntent intent = stripeClient.createPaymentIntent(params);
            log.info("event=payment_intent_created paymentIntentHash={} amount={} currency={}",
                    hash(intent.getId()), amount, stripeProperties.getCurrency());
            return new PaymentIntentSnapshot(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException exception) {
            log.warn("event=payment_intent_create_failed reason={}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment processing is temporarily unavailable; please try again shortly");
        }
    }

    static long toMinorCurrencyUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private static String hash(String value) {
        return value == null ? "none" : Integer.toHexString(value.hashCode());
    }

    public record PaymentIntentSnapshot(String paymentIntentId, String clientSecret, String status) { }
}
