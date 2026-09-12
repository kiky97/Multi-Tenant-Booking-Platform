package com.booking.engine.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for booking lifecycle logic.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.booking")
public class BookingProperties {

    @NotBlank(message = "Booking timezone must be configured")
    private String timezone;

    @Valid
    private HoldLimitsProperties holdLimits = new HoldLimitsProperties();

    @Valid
    private PublicHoldRateLimitProperties publicHoldRateLimit = new PublicHoldRateLimitProperties();

    @Valid
    private PublicActionRateLimitProperties publicActionRateLimit = new PublicActionRateLimitProperties();

    @Getter
    @Setter
    public static class HoldLimitsProperties {

        /**
         * Maximum active public holds allowed for the same employee/date/time slot.
         */
        @Min(value = 1, message = "Public slot hold limit must be at least 1")
        private int maxActivePerSlot = 1;
    }

    @Getter
    @Setter
    public static class PublicHoldRateLimitProperties {

        @Valid
        private RateLimitBucketProperties ip = new RateLimitBucketProperties(12, 60);

        @Valid
        private RateLimitBucketProperties device = new RateLimitBucketProperties(8, 60);

        @Min(value = 1, message = "Public hold rate limit cleanup interval must be at least 1 second")
        private long cleanupIntervalSeconds = 300;
    }

    @Getter
    @Setter
    public static class PublicActionRateLimitProperties {

        @Valid
        private RateLimitBucketProperties ip = new RateLimitBucketProperties(60, 60);

        @Valid
        private RateLimitBucketProperties target = new RateLimitBucketProperties(20, 60);

        @Valid
        private RateLimitBucketProperties device = new RateLimitBucketProperties(30, 60);

        @Min(value = 1, message = "Public action rate limit cleanup interval must be at least 1 second")
        private long cleanupIntervalSeconds = 300;
    }

    @Getter
    @Setter
    public static class RateLimitBucketProperties {

        @Min(value = 1, message = "Rate limit max attempts must be at least 1")
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
}
