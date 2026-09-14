package com.booking.engine.platform.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Caps how many slot holds a single client IP can create per window, independent of TTL expiry. */
@Service
public class RedisBookingHoldRateLimiter {
    private final RedisRateLimiter rateLimiter;
    private final long maxAttempts;
    private final Duration window;

    public RedisBookingHoldRateLimiter(RedisRateLimiter rateLimiter,
            @Value("${app.redis.booking-hold-rate-limit.max-attempts:20}") long maxAttempts,
            @Value("${app.redis.booking-hold-rate-limit.window-seconds:60}") long windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public void check(String clientIp) {
        rateLimiter.enforce("rate-limit:booking-hold:" + clientIp, maxAttempts, window,
                "Too many hold requests; try again shortly");
    }
}
