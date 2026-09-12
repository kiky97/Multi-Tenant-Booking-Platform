package com.booking.engine.properties;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for hair salon settings.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.hair-salon")
public class HairSalonProperties {

    @NotNull(message = "Hair salon ID must be configured")
    private UUID id;
}
