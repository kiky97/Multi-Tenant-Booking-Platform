package com.booking.engine.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.exception.PaymentProcessingException;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StripePaymentServiceImplTest {

    @Mock
    private StripeProperties stripeProperties;

    @Mock
    private StripeClient stripeClient;

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    @InjectMocks
    private StripePaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        when(stripeProperties.getCurrency()).thenReturn("eur");
    }

    @Test
    void createAndConfirmPaymentWithConfirmationTokenReturnsClientSecretAndStatus() throws Exception {
        PaymentIntent intent = org.mockito.Mockito.mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(intent.getId()).thenReturn("pi_checkout");
        when(intent.getClientSecret()).thenReturn("pi_checkout_secret_123");
        when(intent.getAmount()).thenReturn(1000L);
        when(intent.getCurrency()).thenReturn("eur");
        when(intent.getMetadata()).thenReturn(Map.of("bookingId", "123"));
        when(stripeClient.createPaymentIntent(any(PaymentIntentCreateParams.class))).thenReturn(intent);

        BookingCheckoutSessionResponseDto result = service.createAndConfirmPaymentWithConfirmationToken(
                new BigDecimal("10.00"),
                "john@example.com",
                "ctoken_123",
                Map.of("bookingId", "123"));

        ArgumentCaptor<PaymentIntentCreateParams> paramsCaptor = ArgumentCaptor
                .forClass(PaymentIntentCreateParams.class);
        verify(stripeClient).createPaymentIntent(paramsCaptor.capture());
        PaymentIntentCreateParams params = paramsCaptor.getValue();

        assertThat(result.getPaymentIntentId()).isEqualTo("pi_checkout");
        assertThat(result.getClientSecret()).isEqualTo("pi_checkout_secret_123");
        assertThat(result.getPaymentStatus()).isEqualTo("succeeded");
        assertThat(params.getCaptureMethod()).isNull();
        assertThat(params.getPaymentMethodTypes()).containsExactly("card");
        assertThat(params.getConfirmationToken()).isEqualTo("ctoken_123");
        assertThat(params.getConfirm()).isTrue();
    }

    @Test
    void createAndConfirmPaymentReturnsPaymentIntentIdWhenSucceeded() throws Exception {
        PaymentIntent intent = org.mockito.Mockito.mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(intent.getId()).thenReturn("pi_test");
        when(intent.getAmount()).thenReturn(1000L);
        when(intent.getCurrency()).thenReturn("eur");
        when(intent.getMetadata()).thenReturn(Map.of("bookingId", "123"));
        when(stripeClient.createPaymentIntent(any(PaymentIntentCreateParams.class))).thenReturn(intent);

        String id = service.createAndConfirmPayment(
                new BigDecimal("10.00"),
                "john@example.com",
                "pm_card_visa",
                Map.of("bookingId", "123"));

        assertThat(id).isEqualTo("pi_test");
        verify(stripeClient).createPaymentIntent(any(PaymentIntentCreateParams.class));
    }

    @Test
    void createAndConfirmPaymentThrowsWhenStatusNotSucceeded() throws Exception {
        PaymentIntent intent = org.mockito.Mockito.mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("requires_action");
        when(intent.getId()).thenReturn("pi_requires_action");
        when(intent.getAmount()).thenReturn(500L);
        when(intent.getCurrency()).thenReturn("eur");
        when(intent.getMetadata()).thenReturn(Map.of());
        when(stripeClient.createPaymentIntent(any(PaymentIntentCreateParams.class))).thenReturn(intent);

        assertThatThrownBy(() -> service.createAndConfirmPayment(
                new BigDecimal("5.00"),
                "a@b.com",
                "pm",
                Map.of()))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessage("Payment could not be completed. Please try again or use a different payment method.");
    }

    @Test
    void getPaymentIntentStatusReturnsStripeStatus() throws Exception {
        PaymentIntent intent = org.mockito.Mockito.mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(stripeClient.retrievePaymentIntent("pi_status")).thenReturn(intent);

        String status = service.getPaymentIntentStatus("pi_status");

        assertThat(status).isEqualTo("succeeded");
    }

    @Test
    void getPaymentIntentStatusThrowsWhenStripeFails() throws Exception {
        when(stripeClient.retrievePaymentIntent("pi_fail"))
                .thenThrow(new InvalidRequestException("boom", "param", "req", "code", 400, null));

        assertThatThrownBy(() -> service.getPaymentIntentStatus("pi_fail"))
                .isInstanceOfSatisfying(PaymentProcessingException.class, ex -> {
                    assertThat(ex.getClientMessage())
                            .isEqualTo("Unable to verify payment status right now. Please try again.");
                    assertThat(ex.getMessage()).contains("Failed to retrieve Stripe PaymentIntent status: boom");
                    assertThat(ex.getCause()).hasMessageContaining("boom");
                });
    }

    @Test
    void createAndConfirmPaymentWithConfirmationTokenThrowsNeutralMessageAndRetainsCause() throws Exception {
        when(stripeClient.createPaymentIntent(any(PaymentIntentCreateParams.class)))
                .thenThrow(new InvalidRequestException("provider boom", "param", "req", "code", 400, null));

        assertThatThrownBy(() -> service.createAndConfirmPaymentWithConfirmationToken(
                new BigDecimal("10.00"),
                "john@example.com",
                "ctoken_123",
                Map.of("bookingId", "123")))
                .isInstanceOfSatisfying(PaymentProcessingException.class, ex -> {
                    assertThat(ex.getClientMessage())
                            .isEqualTo(
                                    "Payment could not be completed. Please try again or use a different payment method.");
                    assertThat(ex.getMessage()).contains("Stripe payment via confirmation token failed: provider boom");
                    assertThat(ex.getCause()).hasMessageContaining("provider boom");
                });
    }

    @Test
    void createAndConfirmPaymentThrowsNeutralMessageAndRetainsCauseWhenStripeFails() throws Exception {
        when(stripeClient.createPaymentIntent(any(PaymentIntentCreateParams.class)))
                .thenThrow(new InvalidRequestException("card declined", "param", "req", "code", 402, null));

        assertThatThrownBy(() -> service.createAndConfirmPayment(
                new BigDecimal("10.00"),
                "john@example.com",
                "pm_card_visa",
                Map.of("bookingId", "123")))
                .isInstanceOfSatisfying(PaymentProcessingException.class, ex -> {
                    assertThat(ex.getClientMessage())
                            .isEqualTo(
                                    "Payment could not be completed. Please try again or use a different payment method.");
                    assertThat(ex.getMessage()).contains("Stripe payment request failed: card declined");
                    assertThat(ex.getCause()).hasMessageContaining("card declined");
                });
    }

    @Test
    void createAndConfirmPaymentSanitizesProviderDetailsInInternalExceptionMessage() throws Exception {
        when(stripeClient.createPaymentIntent(any(PaymentIntentCreateParams.class)))
                .thenThrow(new InvalidRequestException(
                        "receipt_email john.doe@example.com rejected, call +353 87 123 4567",
                        "param",
                        "req",
                        "code",
                        402,
                        null));

        assertThatThrownBy(() -> service.createAndConfirmPayment(
                new BigDecimal("10.00"),
                "john@example.com",
                "pm_card_visa",
                Map.of("bookingId", "123")))
                .isInstanceOfSatisfying(PaymentProcessingException.class, ex -> {
                    assertThat(ex.getClientMessage())
                            .isEqualTo(
                                    "Payment could not be completed. Please try again or use a different payment method.");
                    assertThat(ex.getMessage()).contains("[emailMask=j***e@example.com]");
                    assertThat(ex.getMessage()).contains("[phoneHash=");
                    assertThat(ex.getMessage()).doesNotContain("john.doe@example.com");
                    assertThat(ex.getMessage()).doesNotContain("+353 87 123 4567");
                });
    }
}
