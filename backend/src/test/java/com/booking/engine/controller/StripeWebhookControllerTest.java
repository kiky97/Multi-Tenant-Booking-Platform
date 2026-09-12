package com.booking.engine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.security.ClientIpResolver;
import com.booking.engine.security.StripeWebhookInvalidRequestRateLimitService;
import com.booking.engine.service.BookingPaymentSyncService;
import com.booking.engine.service.StripePaymentIntentDetails;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripeProperties stripeProperties;
    @Mock
    private BookingPaymentSyncService bookingService;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private StripeWebhookInvalidRequestRateLimitService invalidRequestRateLimitService;

    @InjectMocks
    private StripeWebhookController controller;

    private MockMvc mockMvc;
    private StripeProperties.WebhookProperties webhookProperties;

    @BeforeEach
    void setUp() {
        webhookProperties = new StripeProperties.WebhookProperties();
        webhookProperties.setMaxPayloadBytes(16);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        lenient().when(stripeProperties.getWebhookSecret()).thenReturn("whsec_test");
        lenient().when(stripeProperties.getWebhook()).thenReturn(webhookProperties);
        lenient().when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
    }

    @Test
    void handleWebhookDelegatesSucceededPaymentIntentSync() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_1");
        when(intent.getStatus()).thenReturn("succeeded");
        when(intent.getAmount()).thenReturn(3500L);
        when(intent.getCurrency()).thenReturn("eur");
        when(intent.getMetadata()).thenReturn(Map.of("slotHoldId", "hold-1"));

        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getType()).thenReturn("payment_intent.succeeded");
        when(deserializer.getObject()).thenReturn(Optional.of(intent));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            mockMvc.perform(post("/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "t=sig")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
        }

        verify(bookingService).syncStripePaymentIntentFromWebhook(
                new StripePaymentIntentDetails(
                        "pi_1",
                        "succeeded",
                        3500L,
                        "eur",
                        Map.of("slotHoldId", "hold-1")),
                "payment_intent.succeeded");
    }

    @Test
    void handleWebhookReturns400WhenSignatureMissing() throws Exception {
        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            mockMvc.perform(post("/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("missing signature"));

            webhookMock.verifyNoInteractions();
        }

        verify(invalidRequestRateLimitService).registerInvalidAttempt("203.0.113.10", "missing_signature");
        verifyNoInteractions(bookingService);
    }

    @Test
    void handleWebhookReturns413WhenPayloadTooLarge() throws Exception {
        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
                    mockMvc.perform(post("/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "t=sig")
                            .content("x".repeat(webhookProperties.getMaxPayloadBytes() + 1)))
                    .andExpect(status().is(413))
                    .andExpect(content().string("payload too large"));

            webhookMock.verifyNoInteractions();
        }

        verify(invalidRequestRateLimitService).registerInvalidAttempt("203.0.113.10", "payload_too_large");
        verifyNoInteractions(bookingService);
    }

    @Test
    void handleWebhookReturns400OnSignatureError() throws Exception {
        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(new SignatureVerificationException("bad sig", "header"));

            mockMvc.perform(post("/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "bad")
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("invalid signature"));
        }

        verify(invalidRequestRateLimitService).registerInvalidAttempt("203.0.113.10", "invalid_signature");
        verifyNoInteractions(bookingService);
    }

    @Test
    void handleWebhookReturns429WhenMalformedAttemptsAreRateLimited() throws Exception {
        doThrow(new RateLimitExceededException(
                "Stripe webhook invalid request rate limit exceeded",
                "Too many invalid webhook requests. Please wait a moment and try again."))
                .when(invalidRequestRateLimitService)
                .registerInvalidAttempt(eq("203.0.113.10"), eq("missing_signature"));

        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("too many invalid webhook requests"));

        verifyNoInteractions(bookingService);
    }
}
