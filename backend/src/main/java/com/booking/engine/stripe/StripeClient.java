package com.booking.engine.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;

/**
 * Thin wrapper around Stripe SDK static calls to make payment flows testable.
 */
public interface StripeClient {

    PaymentIntent createPaymentIntent(PaymentIntentCreateParams params) throws StripeException;

    PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException;
}
