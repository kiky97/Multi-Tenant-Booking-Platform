package com.booking.engine.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import com.booking.engine.platform.security.JwtAuthenticationFilter;

/** JWT authentication boundary. Only requires "logged in" at this layer; per-organization role
 * checks (OWNER/ADMIN/STAFF) are enforced by {@link com.booking.engine.platform.security.MembershipGuard}. */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Tomcat forwards to /error internally on response.sendError() (thrown
                        // ResponseStatusException, validation failures, etc.) — without this, that
                        // forward is itself blocked by anyRequest().authenticated(), so every non-2xx
                        // response becomes an empty 403 regardless of the status the controller set.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/providers", "/api/v1/customers").permitAll()
                        // Stripe's servers call the webhook directly (no JWT); its own signature is
                        // the auth. checkout-config only exposes the publishable key, which is public.
                        .requestMatchers("/api/v1/stripe/**").permitAll()
                        .requestMatchers("/api/v1/booking-holds/**").hasAnyRole("CUSTOMER", "PROVIDER")
                        // Fine-grained per-organization role checks (OWNER/ADMIN/STAFF) happen in
                        // MembershipGuard, which needs the :organizationId path variable that a
                        // path-based hasRole() rule here cannot see.
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
