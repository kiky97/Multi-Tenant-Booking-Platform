package com.booking.engine.controller;

import com.booking.engine.dto.AdminSessionResponseDto;
import com.booking.engine.dto.LoginRequestDto;
import com.booking.engine.dto.LoginResponseDto;
import com.booking.engine.security.AdminAuthCookieService;
import com.booking.engine.security.ClientIpResolver;
import com.booking.engine.security.LoginRateLimitService;
import com.booking.engine.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication controller for admin login.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/public/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimitService loginRateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final AdminAuthCookieService adminAuthCookieService;

    /**
     * Performs username/password authentication and establishes the admin auth
     * cookie.
     *
     * @param request login request
     * @return login response with session metadata
     */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminSessionResponseDto> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletRequest httpServletRequest) {
        String clientIp = clientIpResolver.resolve(httpServletRequest);
        loginRateLimitService.registerAttempt(clientIp, request.getUsername());

        LoginResponseDto response = authService.login(request);
        loginRateLimitService.resetSuccessfulAttempt(clientIp, request.getUsername());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, adminAuthCookieService.createSessionCookie(response.getAccessToken())
                        .toString())
                .body(AdminSessionResponseDto.builder()
                        .username(response.getUsername())
                        .expiresInSeconds(response.getExpiresInSeconds())
                        .csrfToken(response.getCsrfToken())
                        .build());
    }
}
