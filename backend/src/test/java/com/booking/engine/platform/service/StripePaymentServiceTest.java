package com.booking.engine.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.booking.engine.platform.config.StripeProperties;
import com.booking.engine.platform.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class StripePaymentServiceTest {

    private StripeClient stripeClient;
    private StripePaymentService paymentService;

    @BeforeEach
    void setUp() {
        stripeClient = mock(StripeClient.class);
        StripeProperties properties = new StripeProperties("sk_test", "whsec_test", "eur", "pk_test", 1_048_576);
        paymentService = new StripePaymentService(stripeClient, properties);
    }

    @Test
    void convertsAmountToMinorUnitsAndSetsCurrencyAndMetadata() throws Exception {
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_123");
        intent.setClientSecret("pi_123_secret");
        intent.setStatus("requires_payment_method");
        when(stripeClient.createPaymentIntent(any(PaymentIntentCreateParams.class))).thenAnswer(invocation -> {
            PaymentIntentCreateParams params = invocation.getArgument(0);
            assertThat(params.getAmount()).isEqualTo(4550L);
            assertThat(params.getCurrency()).isEqualTo("eur");
            assertThat(params.getReceiptEmail()).isEqualTo("customer@example.com");
            assertThat(params.getMetadata()).containsEntry("staffId", "abc");
            return intent;
        });

        StripePaymentService.PaymentIntentSnapshot snapshot = paymentService.createPaymentIntentForBooking(
                new BigDecimal("45.50"), "customer@example.com", Map.of("staffId", "abc"));

        assertThat(snapshot.paymentIntentId()).isEqualTo("pi_123");
        assertThat(snapshot.clientSecret()).isEqualTo("pi_123_secret");
        assertThat(snapshot.status()).isEqualTo("requires_payment_method");
    }

    @Test
    void stripeFailureBecomesAGatewayErrorWithoutLeakingDetails() throws Exception {
        when(stripeClient.createPaymentIntent(any(PaymentIntentCreateParams.class)))
                .thenThrow(new ApiConnectionException("connection refused"));

        assertThatThrownBy(() -> paymentService.createPaymentIntentForBooking(BigDecimal.TEN, "a@b.com", Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
