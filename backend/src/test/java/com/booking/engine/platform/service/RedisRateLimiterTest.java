package com.booking.engine.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RedisRateLimiterTest {

    private final AtomicLong counter = new AtomicLong();
    private StringRedisTemplate redis;
    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        counter.set(0);
        redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenAnswer(invocation -> counter.incrementAndGet());
        rateLimiter = new RedisRateLimiter(redis);
    }

    @Test
    void allowsRequestsUpToTheLimit() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.enforce("key", 5, Duration.ofSeconds(60), "too many requests");
        }
        // No exception means all 5 attempts within the limit were accepted.
        assertThat(counter.get()).isEqualTo(5);
    }

    @Test
    void rejectsOnceTheLimitIsExceeded() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.enforce("key", 5, Duration.ofSeconds(60), "too many requests");
        }

        assertThatThrownBy(() -> rateLimiter.enforce("key", 5, Duration.ofSeconds(60), "too many requests"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void setsExpiryOnlyOnTheFirstRequestInTheWindow() {
        rateLimiter.enforce("key", 5, Duration.ofSeconds(60), "too many requests");
        rateLimiter.enforce("key", 5, Duration.ofSeconds(60), "too many requests");

        verify(redis).expire("key", Duration.ofSeconds(60));
        verify(redis, org.mockito.Mockito.times(1)).expire(anyString(), any(Duration.class));
    }
}
