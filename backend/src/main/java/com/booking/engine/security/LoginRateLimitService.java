package com.booking.engine.security;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.AuthSecurityProperties;
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
 * In-memory rate limiter for admin login attempts.
 */
@Slf4j
@Component
public class LoginRateLimitService {

    private static final String KEY_PREFIX_IP = "ip:";
    private static final String KEY_PREFIX_USERNAME_IP = "user-ip:";

    private final AuthSecurityProperties authSecurityProperties;
    private final SecurityAuditLogger securityAuditLogger;
    private final Clock clock;

    private final ConcurrentMap<String, AttemptWindow> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AttemptWindow> usernameIpAttempts = new ConcurrentHashMap<>();
    private final Object cleanupLock = new Object();
    private volatile Instant nextCleanupAt = Instant.EPOCH;

    public LoginRateLimitService(
            AuthSecurityProperties authSecurityProperties,
            SecurityAuditLogger securityAuditLogger,
            Clock clock) {
        this.authSecurityProperties = authSecurityProperties;
        this.securityAuditLogger = securityAuditLogger;
        this.clock = clock;
    }

    public void registerAttempt(String clientIp, String username) {
        Instant now = Instant.now(clock);
        cleanupIfDue(now);

        String normalizedIp = normalize(clientIp);
        String normalizedUsername = normalize(username);

        if (!consume(ipAttempts,
                KEY_PREFIX_IP + normalizedIp,
                authSecurityProperties.getRateLimit().getIp().getMaxAttempts(),
                Duration.ofSeconds(authSecurityProperties.getRateLimit().getIp().getWindowSeconds()),
                now)) {
            log.warn("event=login_rate_limit_exceeded scope=ip ipFingerprint={}", fingerprint(normalizedIp));
            throw new RateLimitExceededException(AuthSecurityMessages.TOO_MANY_LOGIN_ATTEMPTS);
        }

        if (!normalizedUsername.isBlank()
                && !consume(usernameIpAttempts,
                        KEY_PREFIX_USERNAME_IP + normalizedIp + ":" + normalizedUsername,
                        authSecurityProperties.getRateLimit().getUsernameIp().getMaxAttempts(),
                        Duration.ofSeconds(authSecurityProperties.getRateLimit().getUsernameIp().getWindowSeconds()),
                        now)) {
            log.warn(
                    "event=login_rate_limit_exceeded scope=username_ip ipFingerprint={} principalFingerprint={}",
                    fingerprint(normalizedIp),
                    fingerprint(normalizedUsername));
            throw new RateLimitExceededException(AuthSecurityMessages.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    public void resetSuccessfulAttempt(String clientIp, String username) {
        String normalizedIp = normalize(clientIp);
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isBlank()) {
            return;
        }

        // Keep the broader shared-IP throttle intact so one successful login
        // does not clear recent failures for other usernames behind the same IP.
        usernameIpAttempts.remove(KEY_PREFIX_USERNAME_IP + normalizedIp + ":" + normalizedUsername);
    }

    int trackedWindowCount() {
        return ipAttempts.size() + usernameIpAttempts.size();
    }

    private void cleanupIfDue(Instant now) {
        if (now.isBefore(nextCleanupAt)) {
            return;
        }

        synchronized (cleanupLock) {
            if (now.isBefore(nextCleanupAt)) {
                return;
            }

            cleanupExpired(
                    ipAttempts,
                    Duration.ofSeconds(authSecurityProperties.getRateLimit().getIp().getWindowSeconds()),
                    now);
            cleanupExpired(
                    usernameIpAttempts,
                    Duration.ofSeconds(authSecurityProperties.getRateLimit().getUsernameIp().getWindowSeconds()),
                    now);
            nextCleanupAt = now.plusSeconds(authSecurityProperties.getRateLimit().getCleanupIntervalSeconds());
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
        return securityAuditLogger.hashValue(value);
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
