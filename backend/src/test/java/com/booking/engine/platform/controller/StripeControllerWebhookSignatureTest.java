package com.booking.engine.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.platform.config.StripeProperties;
import com.booking.engine.platform.service.StripePaymentSyncService;
import com.booking.engine.platform.service.StripeWebhookRateLimiter;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Exercises real Stripe HMAC signature verification (not mocked) so the webhook's actual
 * security boundary is under test, not just the code path around it. */
class StripeControllerWebhookSignatureTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    private StripePaymentSyncService paymentSync;
    private StripeWebhookRateLimiter rateLimiter;
    private StripeController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        StripeProperties properties = new StripeProperties("sk_test", WEBHOOK_SECRET, "eur", "pk_test", 1_048_576);
        paymentSync = mock(StripePaymentSyncService.class);
        rateLimiter = mock(StripeWebhookRateLimiter.class);
        controller = new StripeController(properties, paymentSync, rateLimiter);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
    }

    @Test
    void validSignatureOnASucceededEventIsForwardedToTheSyncService() throws Exception {
        String payload = paymentIntentEventPayload("payment_intent.succeeded", "pi_123", 4550, "eur");
        String signature = sign(payload);

        ResponseEntity<String> response = controller.webhook(payload, signature, (long) payload.length(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(paymentSync).handlePaymentIntentEvent("pi_123", "payment_intent.succeeded", 4550L, "eur");
        verify(rateLimiter, never()).check(anyString());
    }

    @Test
    void unrelatedEventTypesAreAcceptedButIgnored() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"charge.succeeded\",\"data\":{\"object\":{\"id\":\"ch_1\",\"object\":\"charge\"}}}";
        String signature = sign(payload);

        ResponseEntity<String> response = controller.webhook(payload, signature, (long) payload.length(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(paymentSync, never()).handlePaymentIntentEvent(any(), any(), any(), any());
    }

    @Test
    void missingSignatureIsRejectedAndRateLimited() {
        ResponseEntity<String> response = controller.webhook("{}", null, 2L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(rateLimiter).check("203.0.113.7");
    }

    @Test
    void tamperedPayloadFailsSignatureVerificationAndIsRateLimited() throws Exception {
        String payload = paymentIntentEventPayload("payment_intent.succeeded", "pi_123", 4550, "eur");
        String signature = sign(payload);
        String tamperedPayload = payload.replace("4550", "1");

        ResponseEntity<String> response = controller.webhook(tamperedPayload, signature, (long) tamperedPayload.length(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("invalid signature");
        verify(rateLimiter).check("203.0.113.7");
        verify(paymentSync, never()).handlePaymentIntentEvent(any(), any(), any(), any());
    }

    @Test
    void payloadOverTheConfiguredLimitIsRejected() {
        StripeProperties tinyLimit = new StripeProperties("sk_test", WEBHOOK_SECRET, "eur", "pk_test", 10);
        StripeController tightController = new StripeController(tinyLimit, paymentSync, rateLimiter);

        ResponseEntity<String> response = tightController.webhook("{\"padding\":\"way more than ten bytes\"}",
                "irrelevant", null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        verify(rateLimiter).check("203.0.113.7");
    }

    private static String paymentIntentEventPayload(String type, String paymentIntentId, long amount, String currency) {
        return "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION
                + "\",\"type\":\"" + type + "\",\"data\":{\"object\":{"
                + "\"id\":\"" + paymentIntentId + "\",\"object\":\"payment_intent\","
                + "\"amount\":" + amount + ",\"currency\":\"" + currency + "\",\"status\":\"succeeded\"}}}";
    }

    /** Builds a real Stripe-Signature header the same way Stripe's own servers do, so
     * {@link Webhook#constructEvent} verifies it exactly as it would a genuine webhook call. */
    private static String sign(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000L;
        String signedPayload = timestamp + "." + payload;
        String signature = Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, signedPayload);
        return "t=" + timestamp + ",v1=" + signature;
    }
}
