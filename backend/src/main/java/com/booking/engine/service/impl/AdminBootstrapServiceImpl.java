package com.booking.engine.service.impl;

import com.booking.engine.service.AdminBootstrapPolicy;
import com.booking.engine.service.AdminBootstrapService;
import com.booking.engine.entity.AdminRole;
import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.properties.AdminBootstrapProperties;
import com.booking.engine.repository.AdminUserRepository;
import com.booking.engine.security.SecurityAuditLogger;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AdminBootstrapService}.
 * Provides admin bootstrap related business operations.
 */
@Service
@RequiredArgsConstructor
public class AdminBootstrapServiceImpl implements AdminBootstrapService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapServiceImpl.class);

    // ---------------------- Repositories ----------------------

    private final AdminUserRepository adminUserRepository;

    // ---------------------- Properties ----------------------

    private final AdminBootstrapProperties properties;

    // ---------------------- Services ----------------------

    private final PasswordEncoder passwordEncoder;

    private final SecurityAuditLogger securityAuditLogger;

    private final AdminBootstrapPolicy adminBootstrapPolicy;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void bootstrapIfEnabled() {
        if (!properties.isEnabled()) {
            return;
        }

        String username = adminBootstrapPolicy.normalize(properties.getUsername());
        String password = properties.getPassword();

        adminBootstrapPolicy.validateRequiredCredentials(username, password);

        log.warn(
                "event=admin_bootstrap_enabled usernameHash={} allowPasswordOverwrite={}",
                hashUsernameForLogs(username),
                properties.isAllowPasswordOverwrite());
        adminBootstrapPolicy.validateBootstrapPassword(password);

        var existingUser = adminUserRepository.findByUsername(username);
        if (existingUser.isEmpty()) {
            adminBootstrapPolicy.validateMayCreateFirstActiveAdmin(adminUserRepository.existsByActiveTrue());
        }

        AdminUserEntity adminUser = existingUser
                .orElseGet(() -> AdminUserEntity.builder()
                        .username(username)
                        .role(AdminRole.ADMIN)
                        .active(true)
                        .build());

        boolean created = adminUser.getId() == null;
        boolean changed = created;
        boolean hasStoredPassword = adminBootstrapPolicy.hasStoredPassword(adminUser);
        boolean passwordMatches = hasStoredPassword && passwordEncoder.matches(password, adminUser.getPasswordHash());
        boolean passwordOverwritten = false;

        changed = adminBootstrapPolicy.ensureBootstrapAccountState(adminUser) || changed;
        adminBootstrapPolicy.validatePasswordOverwriteAllowed(
                created,
                hasStoredPassword,
                passwordMatches,
                properties.isAllowPasswordOverwrite());

        if (!passwordMatches) {
            adminUser.setPasswordHash(passwordEncoder.encode(password));
            changed = true;
            passwordOverwritten = adminBootstrapPolicy.isPasswordOverwrite(created, hasStoredPassword);
            if (passwordOverwritten) {
                adminBootstrapPolicy.incrementTokenVersion(adminUser);
                log.warn(
                        "event=admin_bootstrap_password_overwritten usernameHash={}",
                        hashUsernameForLogs(username));
            }
        }

        if (changed) {
            AdminUserEntity persistedUser = adminUserRepository.save(adminUser);
            log.info(
                    "event=admin_bootstrap_account_updated usernameHash={} created={} passwordOverwritten={}",
                    hashUsernameForLogs(username),
                    created,
                    passwordOverwritten);
            auditBootstrapChange(persistedUser, created, passwordOverwritten);
            return;
        }

        log.info("event=admin_bootstrap_verified usernameHash={}", hashUsernameForLogs(username));
        auditBootstrapVerification(username);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Records the security audit event for successful bootstrap verification.
     */
    private void auditBootstrapVerification(String username) {
        securityAuditLogger.log(securityAuditLogger.event(
                AdminAuditConstants.ADMIN_BOOTSTRAP_EVENT,
                AdminAuditConstants.SUCCESS_OUTCOME)
                .actorUsername(username)
                .resourceType(AdminAuditConstants.ADMIN_ACCOUNT_RESOURCE)
                .resourceId(username)
                .reasonCode(AdminAuditConstants.VERIFIED_REASON)
                .build());
    }

    /*
     * Records the security audit event for created or updated bootstrap accounts.
     */
    private void auditBootstrapChange(AdminUserEntity adminUser, boolean created, boolean passwordOverwritten) {
        Map<String, Object> additionalFields = new LinkedHashMap<>();
        additionalFields.put("tokenVersion", adminUser.getTokenVersion());
        securityAuditLogger.log(securityAuditLogger.event(
                AdminAuditConstants.ADMIN_BOOTSTRAP_EVENT,
                AdminAuditConstants.SUCCESS_OUTCOME)
                .actorUsername(adminUser.getUsername())
                .resourceType(AdminAuditConstants.ADMIN_ACCOUNT_RESOURCE)
                .resourceId(adminUser.getUsername())
                .reasonCode(adminBootstrapPolicy.resolveBootstrapReasonCode(created, passwordOverwritten))
                .additionalFields(additionalFields)
                .build());
    }

    /*
     * Hashes the bootstrap username for operational logs.
     */
    private String hashUsernameForLogs(String username) {
        return securityAuditLogger.hashValue(username);
    }
}
