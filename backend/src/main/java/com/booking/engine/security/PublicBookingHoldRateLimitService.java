package com.booking.engine.security;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.BookingProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * In-memory rate limiter for public temporary slot hold attempts.
 */
@Slf4j
@Component
public class PublicBookingHoldRateLimitService {

    private static final String KEY_PREFIX_IP = "ip:";
    private static final String KEY_PREFIX_DEVICE = "device:";
    private static final String TOO_MANY_HOLD_ATTEMPTS = "Too many appointment hold attempts. Please wait a moment and try again.";

    private final BookingProperties bookingProperties;
    private final Clock clock;

    private final ConcurrentMap<String, AttemptWindow> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AttemptWindow> deviceAttempts = new ConcurrentHashMap<>();
    private final Object cleanupLock = new Object();
    private volatile Instant nextCleanupAt = Instant.EPOCH;

    public PublicBookingHoldRateLimitService(BookingProperties bookingProperties, Clock clock) {
        this.bookingProperties = bookingProperties;
        this.clock = clock;
    }

    public void registerAttempt(String clientIp, String clientDeviceId) {
        Instant now = Instant.now(clock);
        cleanupIfDue(now);

        BookingProperties.PublicHoldRateLimitProperties rateLimit = bookingProperties.getPublicHoldRateLimit();
        String normalizedIp = normalize(clientIp);
        if (!consume(
                ipAttempts,
                KEY_PREFIX_IP + normalizedIp,
                rateLimit.getIp().getMaxAttempts(),
                Duration.ofSeconds(rateLimit.getIp().getWindowSeconds()),
                now)) {
            log.warn(
                    "event=public_booking_hold_rate_limit_exceeded scope=ip clientIpHash={}",
                    fingerprint(normalizedIp));
            throw new RateLimitExceededException("public hold IP rate limit exceeded", TOO_MANY_HOLD_ATTEMPTS);
        }

        String normalizedDeviceId = normalize(clientDeviceId);
        if (!normalizedDeviceId.isBlank()
                && !consume(
                        deviceAttempts,
                        KEY_PREFIX_DEVICE + normalizedDeviceId,
                        rateLimit.getDevice().getMaxAttempts(),
                        Duration.ofSeconds(rateLimit.getDevice().getWindowSeconds()),
                        now)) {
            log.warn(
                    "event=public_booking_hold_rate_limit_exceeded scope=device clientDeviceHash={}",
                    fingerprint(normalizedDeviceId));
            throw new RateLimitExceededException("public hold device rate limit exceeded", TOO_MANY_HOLD_ATTEMPTS);
        }
    }

    int trackedWindowCount() {
        return ipAttempts.size() + deviceAttempts.size();
    }

    private void cleanupIfDue(Instant now) {
        if (now.isBefore(nextCleanupAt)) {
            return;
        }

        synchronized (cleanupLock) {
            if (now.isBefore(nextCleanupAt)) {
                return;
            }

            BookingProperties.PublicHoldRateLimitProperties rateLimit = bookingProperties.getPublicHoldRateLimit();
            cleanupExpired(
                    ipAttempts,
                    Duration.ofSeconds(rateLimit.getIp().getWindowSeconds()),
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
