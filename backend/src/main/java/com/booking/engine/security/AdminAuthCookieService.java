package com.booking.engine.security;

import com.booking.engine.properties.AuthSecurityProperties;
import com.booking.engine.properties.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds and resolves the admin authentication cookie used by the browser-based
 * admin frontend.
 */
@Component
public class AdminAuthCookieService {

    private final AuthSecurityProperties authSecurityProperties;
    private final JwtProperties jwtProperties;

    public AdminAuthCookieService(AuthSecurityProperties authSecurityProperties, JwtProperties jwtProperties) {
        this.authSecurityProperties = authSecurityProperties;
        this.jwtProperties = jwtProperties;
    }

    public ResponseCookie createSessionCookie(String token) {
        return baseCookie(token)
                .maxAge(Duration.ofSeconds(jwtProperties.getExpirationSeconds()))
                .build();
    }

    public ResponseCookie clearSessionCookie() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    public String resolveTokenFromCookie(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }

        String cookieName = getCookieName();
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String getCookieName() {
        return authSecurityProperties.getCookie().getName();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        AuthSecurityProperties.CookieProperties cookieProperties = authSecurityProperties.getCookie();
        return ResponseCookie.from(cookieProperties.getName(), value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path(cookieProperties.getPath());
    }
}
