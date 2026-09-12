package com.booking.engine.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for admin authentication hardening.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthSecurityProperties {

    @Min(value = 1, message = "Maximum failed login attempts must be at least 1")
    private int maxFailedAttempts = 5;

    @Min(value = 1, message = "Account lock duration must be at least 1 second")
    private long lockDurationSeconds = 900;

    @Valid
    private RateLimitProperties rateLimit = new RateLimitProperties();

    @Valid
    private PasswordPolicyProperties passwordPolicy = new PasswordPolicyProperties();

    @Valid
    private CookieProperties cookie = new CookieProperties();

    @Getter
    @Setter
    public static class RateLimitProperties {

        @Valid
        private RateLimitBucketProperties ip = new RateLimitBucketProperties(20, 60);

        @Valid
        private RateLimitBucketProperties usernameIp = new RateLimitBucketProperties(10, 60);

        @Min(value = 1, message = "Rate limit cleanup interval must be at least 1 second")
        private long cleanupIntervalSeconds = 300;
    }

    @Getter
    @Setter
    public static class RateLimitBucketProperties {

        @Min(value = 1, message = "Rate limit maximum attempts must be at least 1")
        private int maxAttempts;

        @Min(value = 1, message = "Rate limit window must be at least 1 second")
        private long windowSeconds;

        public RateLimitBucketProperties() {
        }

        public RateLimitBucketProperties(int maxAttempts, long windowSeconds) {
            this.maxAttempts = maxAttempts;
            this.windowSeconds = windowSeconds;
        }
    }

    @Getter
    @Setter
    public static class PasswordPolicyProperties {

        @Min(value = 8, message = "Admin password minimum length must be at least 8 characters")
        private int minLength = 12;

        private boolean requireUppercase = true;

        private boolean requireLowercase = true;

        private boolean requireDigit = true;

        private boolean requireSpecialCharacter = true;

        private List<String> rejectedValues = new ArrayList<>(List.of(
                "password",
                "password123",
                "admin",
                "admin123",
                "qwerty",
                "qwerty123",
                "letmein",
                "changeme",
                "welcome",
                "secret"));
    }

    @Getter
    @Setter
    public static class CookieProperties {

        private String name = "admin_access_token";

        private String path = "/api/v1/admin";

        private boolean secure = false;

        private String sameSite = "Lax";
    }
}
