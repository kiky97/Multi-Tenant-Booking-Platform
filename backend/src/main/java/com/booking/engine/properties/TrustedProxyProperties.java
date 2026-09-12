package com.booking.engine.properties;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Reverse-proxy addresses that are allowed to supply forwarded headers.
 * When the list is empty, forwarded headers are stripped from all requests.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security")
public class TrustedProxyProperties {

    private List<String> trustedProxyCidrs = new ArrayList<>();
}
