package com.booking.engine.stripe;

import com.booking.engine.properties.StripeProperties;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Component;

/**
 * Default Stripe client delegating to the official stripe-java client instance.
 */
@Component
public class StripeClientImpl implements StripeClient {

    private final com.stripe.StripeClient stripeSdkClient;

    public StripeClientImpl(StripeProperties stripeProperties) {
        this.stripeSdkClient = new com.stripe.StripeClient(stripeProperties.getSecretKey());
    }

    @Override
    public PaymentIntent createPaymentIntent(PaymentIntentCreateParams params) throws StripeException {
        return stripeSdkClient.v1().paymentIntents().create(params);
    }

    @Override
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return stripeSdkClient.v1().paymentIntents().retrieve(paymentIntentId);
    }
}
