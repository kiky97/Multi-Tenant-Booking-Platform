package com.booking.engine.config;

import com.booking.engine.exception.ErrorResponse;
import com.booking.engine.security.AdminAuthCookieService;
import com.booking.engine.security.AdminCookieCsrfHeaderFilter;
import com.booking.engine.security.AdminUserDetailsService;
import com.booking.engine.security.AuthSecurityMessages;
import com.booking.engine.security.JwtAuthenticationFilter;
import com.booking.engine.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security configuration for the Salon Booking Platform API.
 *
 * The API is stateless and uses JWT authentication. Admin authentication is
 * cookie-based, while public booking and Stripe callback endpoints are
 * explicitly whitelisted.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String API_ADMIN = "/api/v1/admin/**";

    private static final String STRIPE_WEBHOOK = "/webhook";

    private static final String ACTUATOR_HEALTH = "/actuator/health";
    private static final String ACTUATOR_HEALTH_NESTED = "/actuator/health/**";

    private static final String ROLE_ADMIN = "ADMIN";

    private static final String API_CONTENT_SECURITY_POLICY = "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'";

    private static final String API_PERMISSIONS_POLICY = "camera=(), geolocation=(), microphone=(), usb=()";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdminUserDetailsService adminUserDetailsService;
    private final JsonMapper jsonMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AdminUserDetailsService adminUserDetailsService,
            JsonMapper jsonMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.adminUserDetailsService = adminUserDetailsService;
        this.jsonMapper = jsonMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            AdminAuthCookieService adminAuthCookieService,
            JwtService jwtService) throws Exception {

        AdminCookieCsrfHeaderFilter adminCookieCsrfHeaderFilter = new AdminCookieCsrfHeaderFilter(
                adminAuthCookieService, jwtService, jsonMapper);

        http
                // Admin authentication uses stateless JWT cookies.
                // Spring CSRF protection is disabled because unsafe admin cookie requests
                // are protected by AdminCookieCsrfHeaderFilter.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(API_CONTENT_SECURITY_POLICY));
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.frameOptions(frame -> frame.deny());
                    headers.referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER));
                    headers.permissionsPolicyHeader(permissions -> permissions.policy(API_PERMISSIONS_POLICY));
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .maxAgeInSeconds(31536000)
                            .includeSubDomains(true));
                })
                .authorizeHttpRequests(auth -> auth
                        // Infrastructure endpoints.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, ACTUATOR_HEALTH).permitAll()
                        .requestMatchers(HttpMethod.GET, ACTUATOR_HEALTH_NESTED).permitAll()

                        // External callbacks.
                        // Stripe signature verification must be performed inside the webhook
                        // handler/service.
                        .requestMatchers(HttpMethod.POST, STRIPE_WEBHOOK).permitAll()

                        // Public storefront read endpoints.
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/hair-salon").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/employees").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/employees/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/treatments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/treatments/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/availability").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/booking-checkout-config").permitAll()

                        // Public authentication endpoint.
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/auth/login").permitAll()

                        // Public booking lifecycle endpoints.
                        // Sensitive lifecycle operations are protected in the service layer
                        // by hold ownership tokens, payment validation, and rate limiting.
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/bookings/{id}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/bookings").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/bookings/hold").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/bookings/{id}/checkout/validate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/bookings/{id}/checkout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/bookings/{id}/confirm").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/public/bookings/{id}").permitAll()

                        // Protected admin API.
                        .requestMatchers(API_ADMIN).hasRole(ROLE_ADMIN)

                        // Everything else is denied by default.
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> writeErrorResponse(
                                res,
                                HttpStatus.UNAUTHORIZED,
                                "Unauthorized",
                                AuthSecurityMessages.AUTHENTICATION_FAILED,
                                req.getRequestURI()))
                        .accessDeniedHandler((req, res, e) -> writeErrorResponse(
                                res,
                                HttpStatus.FORBIDDEN,
                                "Forbidden",
                                "Forbidden",
                                req.getRequestURI())));

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(adminCookieCsrfHeaderFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            HttpStatus status,
            String error,
            String message,
            String path) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                error,
                message,
                path));
    }
}