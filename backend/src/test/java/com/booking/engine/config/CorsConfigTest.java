package com.booking.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.booking.engine.properties.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

    @Test
    void corsConfigurationSourceTrimsOriginsAndRegistersExpectedApiRules() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of(" https://example.com ", "", "https://admin.example.com"));

        CorsConfig config = new CorsConfig(properties);
        CorsConfigurationSource source = config.corsConfigurationSource();

        CorsConfiguration apiConfiguration = source
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/public/employees"));
        CorsConfiguration actuatorConfiguration = source
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/actuator/health"));

        assertThat(apiConfiguration).isNotNull();
        assertThat(apiConfiguration.getAllowedOrigins())
                .containsExactly("https://example.com", "https://admin.example.com");
        assertThat(apiConfiguration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(apiConfiguration.getAllowedHeaders())
                .containsExactly(
                        "Accept",
                        "Authorization",
                        "Content-Type",
                        "Origin",
                        "X-Admin-CSRF",
                        "X-Booking-Device-Id",
                        "X-Booking-Hold-Access-Token",
                        "X-Admin-Hold-Session-Id");
        assertThat(apiConfiguration.getExposedHeaders()).containsExactly("Location");
        assertThat(apiConfiguration.getAllowCredentials()).isTrue();
        assertThat(apiConfiguration.getMaxAge()).isEqualTo(3600L);
        assertThat(actuatorConfiguration).isNotNull();
    }

    @Test
    void corsConfigurationSourceLeavesAllowedOriginsUnsetWhenPropertyListIsEmpty() {
        CorsProperties properties = new CorsProperties();
        CorsConfig config = new CorsConfig(properties);

        CorsConfiguration configuration = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/public/employees"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).isNull();
    }
}
