package com.booking.engine.platform.controller;

import com.booking.engine.platform.config.StripeProperties;
import com.booking.engine.platform.service.StripePaymentSyncService;
import com.booking.engine.platform.service.StripeWebhookRateLimiter;
import com.booking.engine.platform.stripe.StripePaymentIntentEventTypes;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public Stripe endpoints: frontend checkout config and the webhook Stripe calls back on.
 * Both are unauthenticated by necessity (see SecurityConfiguration) — the webhook's only guard is
 * its Stripe signature. */
@RestController
@RequestMapping("/api/v1/stripe")
public class StripeController {
    private final StripeProperties stripeProperties;
    private final StripePaymentSyncService paymentSync;
    private final StripeWebhookRateLimiter rateLimiter;

    public StripeController(StripeProperties stripeProperties, StripePaymentSyncService paymentSync,
            StripeWebhookRateLimiter rateLimiter) {
        this.stripeProperties = stripeProperties;
        this.paymentSync = paymentSync;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/checkout-config")
    public CheckoutConfigView checkoutConfig() {
        return new CheckoutConfigView(stripeProperties.getCurrency(), stripeProperties.getPublishableKey());
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestHeader(value = HttpHeaders.CONTENT_LENGTH, required = false) Long contentLength,
            HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();

        if (signature == null || signature.isBlank()) {
            rateLimiter.check(clientIp);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing signature");
        }
        if (payloadTooLarge(payload, contentLength)) {
            rateLimiter.check(clientIp);
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body("payload too large");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload == null ? "" : payload, signature, stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException exception) {
            rateLimiter.check(clientIp);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        }

        processEvent(event);
        return ResponseEntity.ok("ok");
    }

    private void processEvent(Event event) {
        String type = event.getType();
        if (!isPaymentIntentEvent(type) || event.getDataObjectDeserializer().getObject().isEmpty()) {
            return;
        }
        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().get();
        paymentSync.handlePaymentIntentEvent(intent.getId(), type, intent.getAmount(), intent.getCurrency());
    }

    private boolean isPaymentIntentEvent(String type) {
        return StripePaymentIntentEventTypes.SUCCEEDED.equals(type)
                || StripePaymentIntentEventTypes.CANCELED.equals(type)
                || StripePaymentIntentEventTypes.PAYMENT_FAILED.equals(type);
    }

    private boolean payloadTooLarge(String payload, Long contentLength) {
        int max = stripeProperties.getMaxWebhookPayloadBytes();
        if (contentLength != null && contentLength > max) {
            return true;
        }
        int actualSize = payload == null ? 0 : payload.getBytes(StandardCharsets.UTF_8).length;
        return actualSize > max;
    }

    public record CheckoutConfigView(String currency, String stripePublishableKey) { }
}
