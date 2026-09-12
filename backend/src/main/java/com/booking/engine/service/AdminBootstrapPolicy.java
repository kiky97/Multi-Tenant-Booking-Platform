package com.booking.engine.service;

import com.booking.engine.entity.AdminUserEntity;

/**
 * Service contract for admin bootstrap policy operations.
 * Defines admin bootstrap policy related business operations.
 */
public interface AdminBootstrapPolicy {

    /**
     * Normalizes optional bootstrap text values before validation or lookup.
     *
     * @param value raw value
     * @return trimmed value or null when blank
     */
    String normalize(String value);

    /**
     * Ensures bootstrap username and password are both configured.
     *
     * @param username normalized bootstrap username
     * @param password configured bootstrap password
     */
    void validateRequiredCredentials(String username, String password);

    /**
     * Applies the admin password policy to the bootstrap password.
     *
     * @param password configured bootstrap password
     */
    void validateBootstrapPassword(String password);

    /**
     * Allows first-admin creation only when no active admin account exists.
     *
     * @param activeAdminExists active admin existence flag
     */
    void validateMayCreateFirstActiveAdmin(boolean activeAdminExists);

    /**
     * Ensures a bootstrap admin account is active and has the admin role.
     *
     * @param adminUser admin user entity
     * @return true when the account state was changed
     */
    boolean ensureBootstrapAccountState(AdminUserEntity adminUser);

    /**
     * Checks whether the admin account already has a stored password hash.
     *
     * @param adminUser admin user entity
     * @return true when a password hash is present
     */
    boolean hasStoredPassword(AdminUserEntity adminUser);

    /**
     * Rejects unsafe bootstrap password overwrites for existing accounts.
     *
     * @param created                created flag
     * @param hasStoredPassword      stored password flag
     * @param passwordMatches        password match flag
     * @param allowPasswordOverwrite password overwrite flag
     */
    void validatePasswordOverwriteAllowed(
            boolean created,
            boolean hasStoredPassword,
            boolean passwordMatches,
            boolean allowPasswordOverwrite);

    /**
     * Detects whether bootstrap will replace an existing account password.
     *
     * @param created           created flag
     * @param hasStoredPassword stored password flag
     * @return true when an existing password is being overwritten
     */
    boolean isPasswordOverwrite(boolean created, boolean hasStoredPassword);

    /**
     * Invalidates existing access tokens after a bootstrap password overwrite.
     *
     * @param adminUser admin user entity
     */
    void incrementTokenVersion(AdminUserEntity adminUser);

    /**
     * Resolves the audit reason code for a bootstrap account change.
     *
     * @param created             created flag
     * @param passwordOverwritten password overwrite flag
     * @return audit reason code
     */
    String resolveBootstrapReasonCode(boolean created, boolean passwordOverwritten);
}
