package com.booking.engine.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Shared Stripe configuration, read once and handed to whichever component needs it. */
@Component
public class StripeProperties {
    private final String secretKey;
    private final String webhookSecret;
    private final String currency;
    private final String publishableKey;
    private final int maxWebhookPayloadBytes;

    public StripeProperties(
            @Value("${app.stripe.secret-key:}") String secretKey,
            @Value("${app.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.stripe.currency:eur}") String currency,
            @Value("${app.stripe.publishable-key:}") String publishableKey,
            @Value("${app.stripe.webhook.max-payload-bytes:1048576}") int maxWebhookPayloadBytes) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.currency = currency;
        this.publishableKey = publishableKey;
        this.maxWebhookPayloadBytes = maxWebhookPayloadBytes;
    }

    public String getSecretKey() { return secretKey; }
    public String getWebhookSecret() { return webhookSecret; }
    public String getCurrency() { return currency; }
    public String getPublishableKey() { return publishableKey; }
    public int getMaxWebhookPayloadBytes() { return maxWebhookPayloadBytes; }
}
