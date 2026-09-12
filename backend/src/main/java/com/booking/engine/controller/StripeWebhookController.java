package com.booking.engine.controller;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.security.ClientIpResolver;
import com.booking.engine.security.StripeWebhookInvalidRequestRateLimitService;
import com.booking.engine.service.BookingPaymentSyncService;
import com.booking.engine.service.StripePaymentIntentDetails;
import com.booking.engine.stripe.StripePaymentIntentEventTypes;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook controller for Stripe event callbacks.
 * Verifies signatures and synchronizes booking payment state.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class StripeWebhookController {

    private static final String RESPONSE_INVALID_SIGNATURE = "invalid signature";
    private static final String RESPONSE_MISSING_SIGNATURE = "missing signature";
    private static final String RESPONSE_OK = "ok";
    private static final String RESPONSE_PAYLOAD_TOO_LARGE = "payload too large";
    private static final String RESPONSE_RATE_LIMITED = "too many invalid webhook requests";
    private static final String RESPONSE_WEBHOOK_ERROR = "webhook error";

    private final StripeProperties stripeProperties;
    private final BookingPaymentSyncService bookingService;
    private final ClientIpResolver clientIpResolver;
    private final StripeWebhookInvalidRequestRateLimitService invalidRequestRateLimitService;

    /**
     * Handles Stripe webhook events.
     *
     * @param payload   raw JSON payload
     * @param signature Stripe signature header
     * @return 200 on success, 400 on signature error
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestHeader(value = HttpHeaders.CONTENT_LENGTH, required = false) Long contentLength,
            HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (signature == null || signature.isBlank()) {
            log.warn("event=stripe_webhook outcome=rejected reason=missing_signature");
            return rejectInvalidRequest(clientIp, "missing_signature", HttpStatus.BAD_REQUEST,
                    RESPONSE_MISSING_SIGNATURE);
        }

        if (payloadTooLarge(payload, contentLength)) {
            log.warn("event=stripe_webhook outcome=rejected reason=payload_too_large payloadBytes={} contentLength={}",
                    payloadSize(payload),
                    contentLength);
            return rejectInvalidRequest(clientIp, "payload_too_large", HttpStatus.CONTENT_TOO_LARGE,
                    RESPONSE_PAYLOAD_TOO_LARGE);
        }

        try {
            Event event = Webhook.constructEvent(
                    payload == null ? "" : payload,
                    signature,
                    stripeProperties.getWebhookSecret());
            processEvent(event);
            return ResponseEntity.ok(RESPONSE_OK);
        } catch (SignatureVerificationException ex) {
            log.warn("event=stripe_webhook outcome=invalid_signature reason={}", ex.getClass().getSimpleName());
            return rejectInvalidRequest(clientIp, "invalid_signature", HttpStatus.BAD_REQUEST,
                    RESPONSE_INVALID_SIGNATURE);
        } catch (Exception ex) {
            log.error("event=stripe_webhook outcome=processing_failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(RESPONSE_WEBHOOK_ERROR);
        }
    }

    /*
     * Filters webhook events down to supported PaymentIntent callbacks and forwards
     * the extracted payment data to booking synchronization logic.
     *
     * @param event verified Stripe webhook event
     */
    private void processEvent(Event event) {
        String type = event.getType();
        if (event.getDataObjectDeserializer().getObject().isEmpty()) {
            log.warn("event=stripe_webhook action=skip reason=missing_payload eventType={}", type);
            return;
        }

        if (!isPaymentIntentEvent(type)) {
            log.debug("event=stripe_webhook action=ignore eventType={}", type);
            return;
        }

        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().get();
        bookingService.syncStripePaymentIntentFromWebhook(
                new StripePaymentIntentDetails(
                        intent.getId(),
                        intent.getStatus(),
                        intent.getAmount(),
                        intent.getCurrency(),
                        intent.getMetadata()),
                type);
    }

    /**
     * Checks whether an incoming Stripe event type is one of the PaymentIntent
     * lifecycle events that should mutate booking payment state.
     *
     * @param type Stripe event type
     *
     * @return {@code true} when the event should be processed
     */
    private boolean isPaymentIntentEvent(String type) {
        return StripePaymentIntentEventTypes.SUCCEEDED.equals(type)
                || StripePaymentIntentEventTypes.CANCELED.equals(type)
                || StripePaymentIntentEventTypes.PAYMENT_FAILED.equals(type);
    }

    private boolean payloadTooLarge(String payload, Long contentLength) {
        int maxPayloadBytes = stripeProperties.getWebhook().getMaxPayloadBytes();
        if (contentLength != null && contentLength > maxPayloadBytes) {
            return true;
        }

        return payloadSize(payload) > maxPayloadBytes;
    }

    private int payloadSize(String payload) {
        return payload == null ? 0 : payload.getBytes(StandardCharsets.UTF_8).length;
    }

    private ResponseEntity<String> rejectInvalidRequest(
            String clientIp,
            String reason,
            HttpStatus status,
            String responseBody) {

        try {
            invalidRequestRateLimitService.registerInvalidAttempt(clientIp, reason);
            return ResponseEntity.status(status).body(responseBody);
        } catch (RateLimitExceededException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(RESPONSE_RATE_LIMITED);
        }
    }
}
