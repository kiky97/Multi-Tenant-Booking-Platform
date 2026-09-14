package com.booking.engine.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exercises the slot-hold lifecycle against an in-memory fake of the string commands actually
 * used ({@code SETNX}, {@code GET}, {@code DEL}), so the double-booking guarantee is verified
 * without a real Redis instance.
 */
class RedisBookingHoldServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private StringRedisTemplate redis;
    private RedisBookingHoldService holds;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final Instant startTime = Instant.parse("2026-01-01T10:00:00Z");
    private final Instant endTime = Instant.parse("2026-01-01T10:30:00Z");

    @BeforeEach
    void setUp() {
        store.clear();
        redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            if (store.containsKey(key)) return false;
            store.put(key, value);
            return true;
        });
        when(valueOps.get(anyString())).thenAnswer(invocation -> store.get((String) invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(invocation -> store.remove((String) invocation.getArgument(0)) != null);

        holds = new RedisBookingHoldService(redis, 300);
    }

    @Test
    void secondHoldForSameSlotIsRejectedWhileFirstIsActive() {
        holds.create(organizationId, staffId, startTime, endTime);

        assertThat(catchStatus(() -> holds.create(organizationId, staffId, startTime, endTime)))
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void differentSlotsCanBeHeldConcurrently() {
        RedisBookingHoldService.Hold first = holds.create(organizationId, staffId, startTime, endTime);
        RedisBookingHoldService.Hold second = holds.create(organizationId, staffId,
                startTime.plusSeconds(1800), endTime.plusSeconds(1800));

        assertThat(first.holdToken()).isNotEqualTo(second.holdToken());
    }

    @Test
    void consumeSucceedsOnlyOnceForAValidHold() {
        RedisBookingHoldService.Hold hold = holds.create(organizationId, staffId, startTime, endTime);

        assertThat(holds.consume(hold.holdToken(), organizationId, staffId, startTime, endTime)).isTrue();
        // A confirmed booking burns the hold: a replay of the same token must not succeed again.
        assertThat(holds.consume(hold.holdToken(), organizationId, staffId, startTime, endTime)).isFalse();
    }

    @Test
    void consumeFailsForUnknownToken() {
        assertThat(holds.consume("not-a-real-token", organizationId, staffId, startTime, endTime)).isFalse();
    }

    @Test
    void consumeFailsWhenSlotDetailsDoNotMatchTheHold() {
        RedisBookingHoldService.Hold hold = holds.create(organizationId, staffId, startTime, endTime);
        UUID otherStaff = UUID.randomUUID();

        assertThat(holds.consume(hold.holdToken(), organizationId, otherStaff, startTime, endTime)).isFalse();
        // The mismatched attempt must not have silently consumed the hold for the correct slot.
        assertThat(holds.consume(hold.holdToken(), organizationId, staffId, startTime, endTime)).isTrue();
    }

    @Test
    void releaseFreesTheSlotForANewHold() {
        RedisBookingHoldService.Hold hold = holds.create(organizationId, staffId, startTime, endTime);
        holds.release(hold.holdToken());

        RedisBookingHoldService.Hold replacement = holds.create(organizationId, staffId, startTime, endTime);
        assertThat(replacement).isNotNull();
    }

    @Test
    void releaseOfUnknownTokenIsANoOp() {
        RedisBookingHoldService.Hold hold = holds.create(organizationId, staffId, startTime, endTime);

        holds.release("does-not-exist");

        // The real hold must still be intact: an unrelated bogus token cannot free someone else's slot.
        assertThat(holds.consume(hold.holdToken(), organizationId, staffId, startTime, endTime)).isTrue();
    }

    private static org.springframework.http.HttpStatus catchStatus(Runnable action) {
        try {
            action.run();
        } catch (ResponseStatusException exception) {
            return org.springframework.http.HttpStatus.valueOf(exception.getStatusCode().value());
        }
        throw new AssertionError("Expected ResponseStatusException");
    }
}
