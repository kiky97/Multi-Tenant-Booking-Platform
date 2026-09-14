package com.booking.engine.platform.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RedisLoginRateLimiter {
    private final StringRedisTemplate redis;
    private final long maxAttempts;
    private final Duration window;

    public RedisLoginRateLimiter(StringRedisTemplate redis,
            @Value("${app.redis.login-rate-limit.max-attempts:5}") long maxAttempts,
            @Value("${app.redis.login-rate-limit.window-seconds:60}") long windowSeconds) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public void check(String clientIp) {
        String key = "rate-limit:login:" + clientIp;
        Long attempts = redis.opsForValue().increment(key);
        if (Long.valueOf(1).equals(attempts)) redis.expire(key, window);
        if (attempts != null && attempts > maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts; try again shortly");
        }
    }
}
