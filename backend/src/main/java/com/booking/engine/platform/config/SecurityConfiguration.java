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

/** JWT authentication and provider-only tenant administration boundary. */
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
                        .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/providers", "/api/v1/customers").permitAll()
                        .requestMatchers("/api/v1/booking-holds/**").hasAnyRole("CUSTOMER", "PROVIDER")
                        .anyRequest().hasRole("PROVIDER"))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
