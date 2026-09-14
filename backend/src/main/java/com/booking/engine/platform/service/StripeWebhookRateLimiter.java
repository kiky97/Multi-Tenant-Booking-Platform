package com.booking.engine.platform.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Caps how many invalid (bad signature) webhook requests a single client IP can send per window. */
@Service
public class StripeWebhookRateLimiter {
    private final RedisRateLimiter rateLimiter;
    private final long maxAttempts;
    private final Duration window;

    public StripeWebhookRateLimiter(RedisRateLimiter rateLimiter,
            @Value("${app.stripe.webhook.invalid-rate-limit.max-attempts:20}") long maxAttempts,
            @Value("${app.stripe.webhook.invalid-rate-limit.window-seconds:60}") long windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public void check(String clientIp) {
        rateLimiter.enforce("rate-limit:stripe-webhook-invalid:" + clientIp, maxAttempts, window,
                "Too many invalid webhook requests; try again shortly");
    }
}
