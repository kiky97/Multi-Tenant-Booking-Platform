package com.booking.engine.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Stripe integration.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

    @NotBlank(message = "Stripe secret key must be configured")
    private String secretKey;

    @NotBlank(message = "Stripe webhook secret must be configured")
    private String webhookSecret;

    @NotBlank(message = "Stripe currency must be configured")
    private String currency;

    @NotBlank(message = "Stripe publishable key must be configured")
    private String publishableKey;

    @Valid
    private WebhookProperties webhook = new WebhookProperties();

    @Getter
    @Setter
    public static class WebhookProperties {

        @Min(value = 1024, message = "Stripe webhook payload size limit must be at least 1024 bytes")
        private int maxPayloadBytes = 1048576;

        @Valid
        private RateLimitBucketProperties invalidRateLimit = new RateLimitBucketProperties(20, 60);

        @Min(value = 1, message = "Stripe webhook invalid request rate limit cleanup interval must be at least 1 second")
        private long invalidRateLimitCleanupIntervalSeconds = 300;
    }

    @Getter
    @Setter
    public static class RateLimitBucketProperties {

        @Min(value = 1, message = "Stripe webhook rate limit max attempts must be at least 1")
        private int maxAttempts;

        @Min(value = 1, message = "Stripe webhook rate limit window must be at least 1 second")
        private long windowSeconds;

        public RateLimitBucketProperties() {
        }

        public RateLimitBucketProperties(int maxAttempts, long windowSeconds) {
            this.maxAttempts = maxAttempts;
            this.windowSeconds = windowSeconds;
        }
    }
}
