package com.booking.engine.platform.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Atomic Redis slot holds. Redis automatically releases an unconfirmed hold at TTL expiry. */
@Service
public class RedisBookingHoldService {
    private static final String SLOT_PREFIX = "booking:hold:slot:";
    private static final String TOKEN_PREFIX = "booking:hold:token:";
    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisBookingHoldService(StringRedisTemplate redis,
            @Value("${app.redis.booking-hold-ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Hold create(UUID organizationId, UUID staffId, Instant startTime, Instant endTime) {
        String slotKey = slotKey(organizationId, staffId, startTime, endTime);
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(slotKey, token, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time slot is already held or booked");
        }
        redis.opsForValue().set(TOKEN_PREFIX + token, slotKey, ttl);
        return new Hold(token, startTime.plus(ttl));
    }

    public void release(String token) {
        String tokenKey = TOKEN_PREFIX + token;
        String slotKey = redis.opsForValue().get(tokenKey);
        if (slotKey == null) return;
        String currentToken = redis.opsForValue().get(slotKey);
        if (token.equals(currentToken)) redis.delete(slotKey);
        redis.delete(tokenKey);
    }

    private static String slotKey(UUID organizationId, UUID staffId, Instant startTime, Instant endTime) {
        return SLOT_PREFIX + organizationId + ':' + staffId + ':' + startTime.toEpochMilli() + ':' + endTime.toEpochMilli();
    }

    public record Hold(String holdToken, Instant expiresAt) { }
}
