package com.booking.engine.config;

import com.booking.engine.properties.AuthSecurityProperties;
import com.booking.engine.properties.CorsProperties;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails application startup when deployment-sensitive security settings are
 * unsafe or ambiguous.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ConfigurationSafetyValidator implements ApplicationRunner {

    private static final Set<String> VALID_SAME_SITE_VALUES = Set.of("strict", "lax", "none");
    private static final Set<String> LOCAL_APP_ENV_VALUES = Set.of(
            "local", "localhost", "dev", "development", "test", "testing", "ci");
    private static final List<String> PRODUCTION_HOSTING_ENV_VARS = List.of(
            "APP_ENV",
            "RENDER",
            "RENDER_SERVICE_ID",
            "RENDER_EXTERNAL_HOSTNAME",
            "RAILWAY_ENVIRONMENT",
            "RAILWAY_PROJECT_ID",
            "RAILWAY_SERVICE_ID",
            "FLY_APP_NAME",
            "FLY_REGION",
            "K_SERVICE",
            "K_REVISION",
            "K_CONFIGURATION",
            "DYNO",
            "HEROKU_APP_NAME",
            "VERCEL",
            "VERCEL_ENV",
            "NETLIFY",
            "AWS_EXECUTION_ENV",
            "ECS_CONTAINER_METADATA_URI",
            "ECS_CONTAINER_METADATA_URI_V4",
            "WEBSITE_SITE_NAME",
            "GAE_SERVICE",
            "GAE_ENV",
            "VCAP_APPLICATION",
            "CF_INSTANCE_GUID");

    private final AuthSecurityProperties authSecurityProperties;
    private final CorsProperties corsProperties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        validateProductionProfileRequiredForHostedEnvironments();
        validateSameSiteCookieSettings();
        validateCorsSettings();
        validateProductionProfileSettings();
    }

    private void validateSameSiteCookieSettings() {
        AuthSecurityProperties.CookieProperties cookie = authSecurityProperties.getCookie();
        String sameSite = normalize(cookie.getSameSite());
        if (!VALID_SAME_SITE_VALUES.contains(sameSite.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "app.auth.cookie.same-site must be one of Strict, Lax, or None.");
        }

        if ("none".equals(sameSite.toLowerCase(Locale.ROOT)) && !cookie.isSecure()) {
            throw new IllegalStateException(
                    "app.auth.cookie.secure must be true when app.auth.cookie.same-site is None.");
        }
    }

    private void validateCorsSettings() {
        for (String origin : configuredCorsOrigins()) {
            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "Wildcard CORS origins are not allowed while credentials are enabled.");
            }
        }
    }

    private void validateProductionProfileSettings() {
        if (!isProdProfileActive()) {
            return;
        }

        if (!authSecurityProperties.getCookie().isSecure()) {
            throw new IllegalStateException(
                    "Production profile requires app.auth.cookie.secure=true.");
        }

        List<String> origins = configuredCorsOrigins();
        if (origins.isEmpty() || origins.stream().anyMatch(this::isPlaceholderValue)) {
            throw new IllegalStateException(
                    "Production profile requires exact app.cors.allowed-origins values.");
        }

        if (origins.stream().anyMatch(this::isLocalAddressValue)) {
            throw new IllegalStateException(
                    "Production CORS origins must not point to localhost or loopback addresses.");
        }

        String datasourceUrl = normalize(environment.getProperty("spring.datasource.url"));
        if (datasourceUrl.isEmpty() || isPlaceholderValue(datasourceUrl)) {
            throw new IllegalStateException(
                    "Production profile requires spring.datasource.url to be configured.");
        }

        if (isLocalAddressValue(datasourceUrl)) {
            throw new IllegalStateException(
                    "Production datasource URL must not point to localhost or loopback addresses.");
        }
    }

    private void validateProductionProfileRequiredForHostedEnvironments() {
        if (isProdProfileActive()) {
            return;
        }

        for (String environmentVariable : PRODUCTION_HOSTING_ENV_VARS) {
            String value = normalize(environment.getProperty(environmentVariable));
            if (isProductionHostingSignal(environmentVariable, value)) {
                throw new IllegalStateException(
                        "Production-like deployment detected from " + environmentVariable
                                + "; activate the prod Spring profile before deploying.");
            }
        }
    }

    private boolean isProductionHostingSignal(String environmentVariable, String value) {
        if (value.isEmpty()) {
            return false;
        }

        if ("APP_ENV".equals(environmentVariable)) {
            return !LOCAL_APP_ENV_VALUES.contains(value.toLowerCase(Locale.ROOT));
        }

        return true;
    }

    private List<String> configuredCorsOrigins() {
        List<String> allowedOrigins = corsProperties.getAllowedOrigins() == null
                ? Collections.emptyList()
                : corsProperties.getAllowedOrigins();

        return allowedOrigins.stream()
                .map(this::normalize)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    private boolean isProdProfileActive() {
        for (String activeProfile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(activeProfile)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isPlaceholderValue(String value) {
        return normalize(value).contains("${") || normalize(value).matches("<[^>]+>");
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private boolean isLocalAddressValue(String value) {
        // Intentional security validation: detect loopback addresses to prevent
        // production deployments from using localhost-based endpoints.
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        return normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("[::1]");
    }
}
