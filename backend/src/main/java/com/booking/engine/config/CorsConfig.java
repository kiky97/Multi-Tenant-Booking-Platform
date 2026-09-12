package com.booking.engine.config;

import com.booking.engine.properties.CorsProperties;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Global CORS configuration used by the public and admin APIs when the frontend
 * is hosted on a different domain.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final CorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = corsProperties.getAllowedOrigins().stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(allowedOrigins)) {
            configuration.setAllowedOrigins(allowedOrigins);
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Accept",
                "Authorization",
                "Content-Type",
                "Origin",
                "X-Admin-CSRF",
                "X-Booking-Device-Id",
                "X-Booking-Hold-Access-Token",
                "X-Admin-Hold-Session-Id"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/actuator/**", configuration);
        return source;
    }
}
