package com.booking.engine.controller;

import com.booking.engine.dto.AdminSessionResponseDto;
import com.booking.engine.properties.JwtProperties;
import com.booking.engine.security.AdminAuthCookieService;
import com.booking.engine.security.JwtService;
import com.booking.engine.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated admin auth endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/admin/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminAuthController {

    private final AuthService authService;
    private final AdminAuthCookieService adminAuthCookieService;
    private final JwtProperties jwtProperties;
    private final JwtService jwtService;

    @GetMapping("/session")
    public ResponseEntity<AdminSessionResponseDto> session(Principal principal, HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminSessionResponseDto.builder()
                        .username(principal.getName())
                        .expiresInSeconds(jwtProperties.getExpirationSeconds())
                        .csrfToken(resolveCsrfToken(request))
                        .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal principal) {
        authService.logout(principal.getName());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, adminAuthCookieService.clearSessionCookie().toString())
                .build();
    }

    private String resolveCsrfToken(HttpServletRequest request) {
        String token = adminAuthCookieService.resolveTokenFromCookie(request);
        if (token == null) {
            token = resolveBearerToken(request);
        }

        if (token == null) {
            return null;
        }

        try {
            return jwtService.extractCsrfToken(token);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }
}
