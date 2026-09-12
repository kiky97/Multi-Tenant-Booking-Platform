package com.booking.engine.service.impl;

/**
 * Admin security audit taxonomy shared by admin-facing services.
 */
final class AdminAuditConstants {

    static final String AUTH_LOGIN_EVENT = "AUTH_LOGIN";
    static final String ADMIN_BOOTSTRAP_EVENT = "ADMIN_BOOTSTRAP";
    static final String ADMIN_ACCOUNT_RESOURCE = "ADMIN_ACCOUNT";
    static final String SUCCESS_OUTCOME = "SUCCESS";
    static final String FAILURE_OUTCOME = "FAILURE";
    static final String VERIFIED_REASON = "VERIFIED";
    static final String ACCOUNT_LOCKED_REASON = "ACCOUNT_LOCKED";
    static final String INVALID_CREDENTIALS_REASON = "INVALID_CREDENTIALS";

    private AdminAuditConstants() {
    }
}
