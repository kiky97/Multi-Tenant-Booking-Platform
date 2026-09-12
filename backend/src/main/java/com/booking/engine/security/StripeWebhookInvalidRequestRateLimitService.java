package com.booking.engine.security;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.StripeProperties;
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
 * In-memory rate limiter for malformed Stripe webhook attempts.
 */
@Slf4j
@Component
public class StripeWebhookInvalidRequestRateLimitService {

    private static final String KEY_PREFIX_IP = "ip:";
    private static final String TOO_MANY_INVALID_WEBHOOKS =
            "Too many invalid webhook requests. Please wait a moment and try again.";

    private final StripeProperties stripeProperties;
    private final Clock clock;

    private final ConcurrentMap<String, AttemptWindow> ipAttempts = new ConcurrentHashMap<>();
    private final Object cleanupLock = new Object();
    private volatile Instant nextCleanupAt = Instant.EPOCH;

    public StripeWebhookInvalidRequestRateLimitService(StripeProperties stripeProperties, Clock clock) {
        this.stripeProperties = stripeProperties;
        this.clock = clock;
    }

    public void registerInvalidAttempt(String clientIp, String reason) {
        Instant now = Instant.now(clock);
        cleanupIfDue(now);

        StripeProperties.RateLimitBucketProperties rateLimit =
                stripeProperties.getWebhook().getInvalidRateLimit();
        String normalizedIp = normalize(clientIp);
        if (!consume(
                ipAttempts,
                KEY_PREFIX_IP + normalizedIp,
                rateLimit.getMaxAttempts(),
                Duration.ofSeconds(rateLimit.getWindowSeconds()),
                now)) {
            log.warn(
                    "event=stripe_webhook_invalid_rate_limit_exceeded clientIpHash={} reason={}",
                    fingerprint(normalizedIp),
                    normalizeReason(reason));
            throw new RateLimitExceededException(
                    "Stripe webhook invalid request rate limit exceeded",
                    TOO_MANY_INVALID_WEBHOOKS);
        }
    }

    int trackedWindowCount() {
        return ipAttempts.size();
    }

    private void cleanupIfDue(Instant now) {
        if (now.isBefore(nextCleanupAt)) {
            return;
        }

        synchronized (cleanupLock) {
            if (now.isBefore(nextCleanupAt)) {
                return;
            }

            StripeProperties.WebhookProperties webhook = stripeProperties.getWebhook();
            cleanupExpired(
                    ipAttempts,
                    Duration.ofSeconds(webhook.getInvalidRateLimit().getWindowSeconds()),
                    now);
            nextCleanupAt = now.plusSeconds(webhook.getInvalidRateLimitCleanupIntervalSeconds());
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

    private String normalizeReason(String reason) {
        String normalized = normalize(reason);
        return normalized.isBlank() ? "unknown" : normalized;
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
