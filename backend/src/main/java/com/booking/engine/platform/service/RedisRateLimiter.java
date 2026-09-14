package com.booking.engine.platform.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Generic fixed-window request counter backed by Redis {@code INCR}/{@code EXPIRE}. */
@Component
public class RedisRateLimiter {
    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Increments the counter for {@code key} and throws 429 once {@code maxAttempts} is exceeded
     * within {@code window}. The window resets on the first request after it lapses.
     */
    public void enforce(String key, long maxAttempts, Duration window, String message) {
        Long attempts = redis.opsForValue().increment(key);
        if (Long.valueOf(1).equals(attempts)) {
            redis.expire(key, window);
        }
        if (attempts != null && attempts > maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }
}
