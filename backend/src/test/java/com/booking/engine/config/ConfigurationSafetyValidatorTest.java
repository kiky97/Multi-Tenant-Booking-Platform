package com.booking.engine.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.engine.properties.AuthSecurityProperties;
import com.booking.engine.properties.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class ConfigurationSafetyValidatorTest {

    @Test
    void runAllowsSafeLocalDefaults() {
        AuthSecurityProperties authProperties = authProperties("Lax", false);
        CorsProperties corsProperties = corsProperties("http://localhost:5173");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/booking_engine");

        assertThatCode(() -> validator(authProperties, corsProperties, environment).run(arguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void runAllowsExplicitLocalAppEnvironmentWithoutProdProfile() {
        AuthSecurityProperties authProperties = authProperties("Lax", false);
        CorsProperties corsProperties = corsProperties("http://localhost:5173");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_ENV", "development")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/booking_engine");

        assertThatCode(() -> validator(authProperties, corsProperties, environment).run(arguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void runRejectsProductionLikeEnvironmentWithoutProdProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("RENDER", "true")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/booking_engine");

        assertThatThrownBy(() -> validator(safeAuthProperties(), safeCorsProperties(), environment).run(arguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production-like deployment detected from RENDER; "
                        + "activate the prod Spring profile before deploying.");
    }

    @Test
    void runRejectsProductionLikeAppEnvironmentWithoutProdProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_ENV", "production")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/booking_engine");

        assertThatThrownBy(() -> validator(safeAuthProperties(), safeCorsProperties(), environment).run(arguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production-like deployment detected from APP_ENV; "
                        + "activate the prod Spring profile before deploying.");
    }

    @Test
    void runAllowsProductionLikeEnvironmentWhenProdProfileHasSecureValues() {
        MockEnvironment environment = safeProdEnvironment()
                .withProperty("RENDER", "true");

        assertThatCode(() -> validator(safeAuthProperties(), safeCorsProperties(), environment).run(arguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void runRejectsInvalidSameSiteValue() {
        AuthSecurityProperties authProperties = authProperties("Relaxed", true);

        assertThatThrownBy(() -> validator(authProperties, safeCorsProperties(), safeProdEnvironment()).run(arguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.auth.cookie.same-site must be one of Strict, Lax, or None.");
    }

    @Test
    void runRejectsSameSiteNoneWithoutSecureCookie() {
        AuthSecurityProperties authProperties = authProperties("None", false);

        assertThatThrownBy(() -> validator(authProperties, safeCorsProperties(), safeProdEnvironment()).run(arguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.auth.cookie.secure must be true when app.auth.cookie.same-site is None.");
    }

    @Test
    void runRejectsWildcardCorsOriginsBecauseCredentialsAreEnabled() {
        CorsProperties corsProperties = corsProperties("https://example.com", "*");

        assertThatThrownBy(() -> validator(safeAuthProperties(), corsProperties, safeProdEnvironment()).run(arguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Wildcard CORS origins are not allowed while credentials are enabled.");
    }

    @Test
    void runRejectsLocalCorsOriginInProdProfile() {
        CorsProperties corsProperties = corsProperties("https://example.com", "http://localhost:5173");

        assertThatThrownBy(() -> validator(safeAuthProperties(), corsProperties, safeProdEnvironment()).run(arguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production CORS origins must not point to localhost or loopback addresses.");
    }

    @Test
    void runRejectsLocalDatasourceUrlInProdProfile() {
        MockEnvironment environment = prodEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/booking_engine");

        assertThatThrownBy(() -> validator(safeAuthProperties(), safeCorsProperties(), environment).run(arguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production datasource URL must not point to localhost or loopback addresses.");
    }

    @Test
    void runAllowsSafeProdProfileSettings() {
        assertThatCode(() -> validator(safeAuthProperties(), safeCorsProperties(), safeProdEnvironment()).run(arguments()))
                .doesNotThrowAnyException();
    }

    private ConfigurationSafetyValidator validator(
            AuthSecurityProperties authProperties,
            CorsProperties corsProperties,
            MockEnvironment environment) {
        return new ConfigurationSafetyValidator(authProperties, corsProperties, environment);
    }

    private DefaultApplicationArguments arguments() {
        return new DefaultApplicationArguments(new String[0]);
    }

    private AuthSecurityProperties safeAuthProperties() {
        return authProperties("None", true);
    }

    private AuthSecurityProperties authProperties(String sameSite, boolean secure) {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getCookie().setSameSite(sameSite);
        properties.getCookie().setSecure(secure);
        return properties;
    }

    private CorsProperties safeCorsProperties() {
        return corsProperties("https://frontend.example");
    }

    private CorsProperties corsProperties(String... origins) {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of(origins));
        return properties;
    }

    private MockEnvironment safeProdEnvironment() {
        return prodEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example:5432/booking_engine");
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
