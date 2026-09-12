package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveLogSanitizerTest {

    @Test
    void sanitizeForLogsMasksEmailsAndHashesPhoneNumbers() {
        String sanitized = SensitiveLogSanitizer.sanitizeForLogs(
                "Customer john.doe@example.com requested call back on +353 87 123 4567");

        assertThat(sanitized).contains("[emailMask=j***e@example.com]");
        assertThat(sanitized).contains("[phoneHash=");
        assertThat(sanitized).doesNotContain("john.doe@example.com");
        assertThat(sanitized).doesNotContain("+353 87 123 4567");
    }

    @Test
    void sanitizeForLogsKeepsDatesVisibleWhileRedactingContactValues() {
        String sanitized = SensitiveLogSanitizer.sanitizeForLogs(
                "Rejected value [2026-04-21] for bookingDate and phone [353871234567]");

        assertThat(sanitized).contains("2026-04-21");
        assertThat(sanitized).contains("[phoneHash=");
        assertThat(sanitized).doesNotContain("353871234567");
    }

    @Test
    void sanitizeForLogsRedactsAuthAndPaymentSecrets() {
        String sanitized = SensitiveLogSanitizer.sanitizeForLogs(
                "Authorization: Bearer header.payload.signature client_secret=pi_secret_123 "
                        + "payment_method=pm_123 password=hunter2 token=raw-token");

        assertThat(sanitized).contains("Authorization=[redacted]");
        assertThat(sanitized).contains("client_secret=[redacted]");
        assertThat(sanitized).contains("payment_method=[redacted]");
        assertThat(sanitized).contains("password=[redacted]");
        assertThat(sanitized).contains("token=[redacted]");
        assertThat(sanitized).doesNotContain("header.payload.signature");
        assertThat(sanitized).doesNotContain("pi_secret_123");
        assertThat(sanitized).doesNotContain("pm_123");
        assertThat(sanitized).doesNotContain("hunter2");
        assertThat(sanitized).doesNotContain("raw-token");
    }
}
