package com.booking.engine.security;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.BookingProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * In-memory rate limiter for public booking lifecycle actions that can touch
 * locked booking state or payment services.
 */
@Slf4j
@Component
public class PublicBookingActionRateLimitService {

    private static final String KEY_PREFIX_IP = "ip:";
    private static final String KEY_PREFIX_TARGET = "target:";
    private static final String KEY_PREFIX_DEVICE = "device:";
    private static final String TOO_MANY_BOOKING_REQUESTS =
            "Too many booking requests. Please wait a moment and try again.";

    private final BookingProperties bookingProperties;
    private final Clock clock;

    private final ConcurrentMap<String, AttemptWindow> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AttemptWindow> targetAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AttemptWindow> deviceAttempts = new ConcurrentHashMap<>();
    private final Object cleanupLock = new Object();
    private volatile Instant nextCleanupAt = Instant.EPOCH;

    public PublicBookingActionRateLimitService(BookingProperties bookingProperties, Clock clock) {
        this.bookingProperties = bookingProperties;
        this.clock = clock;
    }

    public void registerAttempt(Action action, String clientIp, UUID bookingOrHoldId, String clientDeviceId) {
        Instant now = Instant.now(clock);
        cleanupIfDue(now);

        BookingProperties.PublicActionRateLimitProperties rateLimit = bookingProperties.getPublicActionRateLimit();
        String normalizedIp = normalize(clientIp);
        String actionPrefix = normalizeAction(action);
        String ipKey = actionPrefix + "|" + KEY_PREFIX_IP + normalizedIp;
        if (!consume(
                ipAttempts,
                ipKey,
                rateLimit.getIp().getMaxAttempts(),
                Duration.ofSeconds(rateLimit.getIp().getWindowSeconds()),
                now)) {
            log.warn(
                    "event=public_booking_action_rate_limit_exceeded action={} scope=ip clientIpHash={}",
                    actionPrefix,
                    fingerprint(normalizedIp));
            throw new RateLimitExceededException("public booking action IP rate limit exceeded",
                    TOO_MANY_BOOKING_REQUESTS);
        }

        if (bookingOrHoldId != null) {
            String targetKey = actionPrefix + "|" + KEY_PREFIX_IP + normalizedIp
                    + "|" + KEY_PREFIX_TARGET + bookingOrHoldId;
            if (!consume(
                    targetAttempts,
                    targetKey,
                    rateLimit.getTarget().getMaxAttempts(),
                    Duration.ofSeconds(rateLimit.getTarget().getWindowSeconds()),
                    now)) {
                log.warn(
                        "event=public_booking_action_rate_limit_exceeded action={} scope=target clientIpHash={} targetId={}",
                        actionPrefix,
                        fingerprint(normalizedIp),
                        bookingOrHoldId);
                throw new RateLimitExceededException("public booking action target rate limit exceeded",
                        TOO_MANY_BOOKING_REQUESTS);
            }
        }

        String normalizedDeviceId = normalize(clientDeviceId);
        if (!normalizedDeviceId.isBlank()) {
            String deviceKey = actionPrefix + "|" + KEY_PREFIX_IP + normalizedIp
                    + "|" + KEY_PREFIX_DEVICE + normalizedDeviceId;
            if (!consume(
                    deviceAttempts,
                    deviceKey,
                    rateLimit.getDevice().getMaxAttempts(),
                    Duration.ofSeconds(rateLimit.getDevice().getWindowSeconds()),
                    now)) {
                log.warn(
                        "event=public_booking_action_rate_limit_exceeded action={} scope=device clientIpHash={} clientDeviceHash={}",
                        actionPrefix,
                        fingerprint(normalizedIp),
                        fingerprint(normalizedDeviceId));
                throw new RateLimitExceededException("public booking action device rate limit exceeded",
                        TOO_MANY_BOOKING_REQUESTS);
            }
        }
    }

    int trackedWindowCount() {
        return ipAttempts.size() + targetAttempts.size() + deviceAttempts.size();
    }

    private void cleanupIfDue(Instant now) {
        if (now.isBefore(nextCleanupAt)) {
            return;
        }

        synchronized (cleanupLock) {
            if (now.isBefore(nextCleanupAt)) {
                return;
            }

            BookingProperties.PublicActionRateLimitProperties rateLimit = bookingProperties.getPublicActionRateLimit();
            cleanupExpired(
                    ipAttempts,
                    Duration.ofSeconds(rateLimit.getIp().getWindowSeconds()),
                    now);
            cleanupExpired(
                    targetAttempts,
                    Duration.ofSeconds(rateLimit.getTarget().getWindowSeconds()),
                    now);
            cleanupExpired(
                    deviceAttempts,
                    Duration.ofSeconds(rateLimit.getDevice().getWindowSeconds()),
                    now);
            nextCleanupAt = now.plusSeconds(rateLimit.getCleanupIntervalSeconds());
        }
    }

    private void cleanupExpired(
            ConcurrentMap<String, AttemptWindow> windows,
            Duration window,
            Instant now) {

        Instant cutoff = now.minus(window);
        windows.forEach((key, ignored) -> windows.computeIfPresent(key,
                (currentKey, attemptWindow) -> attemptWindow.pruneAndIsEmpty(cutoff) ? null : attemptWindow));
    }

    private boolean consume(
            ConcurrentMap<String, AttemptWindow> windows,
            String key,
            int maxAttempts,
            Duration window,
            Instant now) {

        AtomicBoolean allowed = new AtomicBoolean();
        windows.compute(key, (ignored, existingWindow) -> {
            AttemptWindow attemptWindow = existingWindow == null ? new AttemptWindow() : existingWindow;
            allowed.set(attemptWindow.tryConsume(now, maxAttempts, window));
            return attemptWindow.isEmpty() ? null : attemptWindow;
        });
        return allowed.get();
    }

    private String normalizeAction(Action action) {
        if (action == null) {
            return "unknown";
        }

        return action.name().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }

    private String fingerprint(String value) {
        return SensitiveLogSanitizer.hashValue(value);
    }

    public enum Action {
        DIRECT_CREATE,
        CHECKOUT_VALIDATE,
        CHECKOUT_PREPARE,
        CONFIRM,
        CANCEL
    }

    private static final class AttemptWindow {

        private final Deque<Instant> attempts = new ArrayDeque<>();

        synchronized boolean tryConsume(Instant now, int maxAttempts, Duration window) {
            prune(now.minus(window));
            if (attempts.size() >= maxAttempts) {
                return false;
            }

            attempts.addLast(now);
            return true;
        }

        synchronized boolean isEmpty() {
            return attempts.isEmpty();
        }

        synchronized boolean pruneAndIsEmpty(Instant cutoff) {
            prune(cutoff);
            return attempts.isEmpty();
        }

        private void prune(Instant cutoff) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.removeFirst();
            }
        }
    }
}
