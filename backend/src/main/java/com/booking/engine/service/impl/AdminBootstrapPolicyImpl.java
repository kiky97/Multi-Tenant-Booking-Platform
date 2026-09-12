package com.booking.engine.service.impl;

import com.booking.engine.service.AdminBootstrapPolicy;
import com.booking.engine.entity.AdminRole;
import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.security.AdminPasswordPolicyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link AdminBootstrapPolicy}.
 * Provides admin bootstrap policy related business operations.
 */
@Service
@RequiredArgsConstructor
public class AdminBootstrapPolicyImpl implements AdminBootstrapPolicy {
    // ---------------------- Services ----------------------

    private final AdminPasswordPolicyValidator adminPasswordPolicyValidator;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRequiredCredentials(String username, String password) {
        if (username == null || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Admin bootstrap is enabled, but APP_ADMIN_BOOTSTRAP_USERNAME "
                            + "or APP_ADMIN_BOOTSTRAP_PASSWORD is missing.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateBootstrapPassword(String password) {
        try {
            adminPasswordPolicyValidator.validate(password);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Admin bootstrap password does not meet security requirements.",
                    exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateMayCreateFirstActiveAdmin(boolean activeAdminExists) {
        if (activeAdminExists) {
            throw new IllegalStateException(
                    "Admin bootstrap may only create the first active admin account. "
                            + "An active admin already exists, so bootstrap must target that existing username for "
                            + "verification or explicit password rotation.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean ensureBootstrapAccountState(AdminUserEntity adminUser) {
        boolean changed = false;

        if (adminUser.getRole() != AdminRole.ADMIN) {
            adminUser.setRole(AdminRole.ADMIN);
            changed = true;
        }

        if (!Boolean.TRUE.equals(adminUser.getActive())) {
            adminUser.setActive(true);
            changed = true;
        }

        return changed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasStoredPassword(AdminUserEntity adminUser) {
        return adminUser.getPasswordHash() != null && !adminUser.getPasswordHash().isBlank();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePasswordOverwriteAllowed(
            boolean created,
            boolean hasStoredPassword,
            boolean passwordMatches,
            boolean allowPasswordOverwrite) {
        if (!created && hasStoredPassword && !passwordMatches && !allowPasswordOverwrite) {
            throw new IllegalStateException(
                    "Admin bootstrap detected an existing password for this user. "
                            + "Set APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE=true to rotate it explicitly.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPasswordOverwrite(boolean created, boolean hasStoredPassword) {
        return !created && hasStoredPassword;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementTokenVersion(AdminUserEntity adminUser) {
        adminUser.setTokenVersion(Math.max(0, adminUser.getTokenVersion()) + 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveBootstrapReasonCode(boolean created, boolean passwordOverwritten) {
        if (created) {
            return "CREATED";
        }
        if (passwordOverwritten) {
            return "PASSWORD_OVERWRITE";
        }
        return "UPDATED";
    }
}
