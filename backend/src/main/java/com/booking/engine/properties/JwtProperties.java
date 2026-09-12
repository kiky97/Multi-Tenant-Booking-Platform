package com.booking.engine.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for JWT authentication.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    @NotBlank(message = "JWT secret must be configured")
    private String secret;

    @Min(value = 300, message = "JWT expiration must be at least 300 seconds")
    private long expirationSeconds;

    @NotBlank(message = "JWT issuer must be configured")
    private String issuer;
}
