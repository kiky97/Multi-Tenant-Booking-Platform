package com.booking.engine.security;

import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.properties.AuthSecurityProperties;
import com.booking.engine.repository.AdminUserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks failed admin logins and applies temporary account locking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthenticationSecurityService {

    private final AdminUserRepository adminUserRepository;
    private final AuthSecurityProperties authSecurityProperties;
    private final SecurityAuditLogger securityAuditLogger;

    @Transactional
    public void registerFailedLogin(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        adminUserRepository.findByUsernameAndActiveTrueForUpdate(username)
                .ifPresent(user -> applyFailedLogin(user, LocalDateTime.now()));
    }

    @Transactional
    public void registerSuccessfulLogin(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        adminUserRepository.findByUsernameAndActiveTrueForUpdate(username)
                .ifPresent(this::resetFailedLoginState);
    }

    @Transactional
    public void logout(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        adminUserRepository.findByUsernameAndActiveTrueForUpdate(username)
                .ifPresent(user -> {
                    incrementTokenVersion(user);
                    securityAuditLogger.log(securityAuditLogger.event("AUTH_LOGOUT", "SUCCESS")
                            .actorUsername(user.getUsername())
                            .resourceType("ADMIN_ACCOUNT")
                            .resourceId(user.getUsername())
                            .reasonCode("LOGOUT")
                            .build());
                });
    }

    public boolean isLocked(AdminUserEntity user, LocalDateTime now) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
    }

    private void applyFailedLogin(AdminUserEntity user, LocalDateTime now) {
        if (isLocked(user, now)) {
            log.warn("event=admin_authentication_rejected reason=account_locked principalFingerprint={}",
                    fingerprint(user.getUsername()));
            return;
        }

        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        int failedAttempts = Math.max(user.getFailedLoginAttempts(), 0) + 1;
        user.setFailedLoginAttempts(failedAttempts);
        user.setLastFailedLoginAt(now);

        if (failedAttempts >= authSecurityProperties.getMaxFailedAttempts()) {
            LocalDateTime lockedUntil = now.plusSeconds(authSecurityProperties.getLockDurationSeconds());
            user.setLockedUntil(lockedUntil);
            incrementTokenVersion(user);
            Map<String, Object> additionalFields = new LinkedHashMap<>();
            String principalFingerprint = fingerprint(user.getUsername());
            additionalFields.put("failedAttempts", failedAttempts);
            additionalFields.put("lockedUntil", lockedUntil);
            additionalFields.put("tokenVersion", user.getTokenVersion());
            if (principalFingerprint != null) {
                additionalFields.put("principalFingerprint", principalFingerprint);
            }
            securityAuditLogger.log(securityAuditLogger.event("AUTH_ACCOUNT_LOCK", "SUCCESS")
                    .resourceType("ADMIN_ACCOUNT")
                    .reasonCode("FAILED_LOGIN_THRESHOLD_REACHED")
                    .additionalFields(additionalFields)
                    .build());
            log.warn(
                    "event=admin_account_locked principalFingerprint={} lockedUntil={}",
                    fingerprint(user.getUsername()),
                    lockedUntil);
        }
    }

    private void resetFailedLoginState(AdminUserEntity user) {
        if (user.getFailedLoginAttempts() == 0
                && user.getLockedUntil() == null
                && user.getLastFailedLoginAt() == null) {
            return;
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastFailedLoginAt(null);
    }

    private void incrementTokenVersion(AdminUserEntity user) {
        user.setTokenVersion(Math.max(0, user.getTokenVersion()) + 1);
    }

    private String fingerprint(String value) {
        return securityAuditLogger.hashValue(value);
    }
}
