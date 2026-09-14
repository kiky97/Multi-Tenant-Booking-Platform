package com.booking.engine.platform.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RedisLoginRateLimiter {
    private final RedisRateLimiter rateLimiter;
    private final long maxAttempts;
    private final Duration window;

    public RedisLoginRateLimiter(RedisRateLimiter rateLimiter,
            @Value("${app.redis.login-rate-limit.max-attempts:5}") long maxAttempts,
            @Value("${app.redis.login-rate-limit.window-seconds:60}") long windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public void check(String clientIp) {
        rateLimiter.enforce("rate-limit:login:" + clientIp, maxAttempts, window,
                "Too many login attempts; try again shortly");
    }
}
