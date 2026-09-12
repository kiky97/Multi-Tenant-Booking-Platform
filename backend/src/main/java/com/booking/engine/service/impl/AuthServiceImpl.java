package com.booking.engine.service.impl;

import com.booking.engine.dto.LoginRequestDto;
import com.booking.engine.dto.LoginResponseDto;
import com.booking.engine.properties.JwtProperties;
import com.booking.engine.security.AdminAuthenticationSecurityService;
import com.booking.engine.security.AdminUserDetailsService;
import com.booking.engine.security.JwtService;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.service.AuthService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link AuthService}.
 * Provides auth related business operations.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    // ---------------------- Services ----------------------

    private final AuthenticationManager authenticationManager;

    private final JwtService jwtService;

    private final AdminAuthenticationSecurityService adminAuthenticationSecurityService;

    private final AdminUserDetailsService adminUserDetailsService;

    private final SecurityAuditLogger securityAuditLogger;

    // ---------------------- Properties ----------------------

    private final JwtProperties jwtProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            adminAuthenticationSecurityService.registerSuccessfulLogin(userDetails.getUsername());
            int tokenVersion = adminUserDetailsService.loadTokenVersionByUsername(userDetails.getUsername());
            String csrfToken = jwtService.generateCsrfToken();
            String token = jwtService.generateToken(
                    userDetails,
                    Map.of(JwtService.CSRF_TOKEN_CLAIM, csrfToken),
                    tokenVersion);
            String principalFingerprint = principalFingerprint(userDetails.getUsername());
            securityAuditLogger.log(securityAuditLogger.event(
                    AdminAuditConstants.AUTH_LOGIN_EVENT,
                    AdminAuditConstants.SUCCESS_OUTCOME)
                    .actorUsername(userDetails.getUsername())
                    .resourceType(AdminAuditConstants.ADMIN_ACCOUNT_RESOURCE)
                    .resourceId(userDetails.getUsername())
                    .build());
            log.info("event=admin_login_succeeded principalFingerprint={}", principalFingerprint);

            return LoginResponseDto.builder()
                    .username(userDetails.getUsername())
                    .accessToken(token)
                    .csrfToken(csrfToken)
                    .expiresInSeconds(jwtProperties.getExpirationSeconds())
                    .build();
        } catch (AuthenticationException ex) {
            if (!(ex instanceof LockedException)) {
                adminAuthenticationSecurityService.registerFailedLogin(request.getUsername());
            }
            Map<String, Object> additionalFields = new LinkedHashMap<>();
            String principalFingerprint = principalFingerprint(request.getUsername());
            if (principalFingerprint != null) {
                additionalFields.put("principalFingerprint", principalFingerprint);
            }
            String reasonCode = ex instanceof LockedException
                    ? AdminAuditConstants.ACCOUNT_LOCKED_REASON
                    : AdminAuditConstants.INVALID_CREDENTIALS_REASON;
            securityAuditLogger.log(securityAuditLogger.event(
                    AdminAuditConstants.AUTH_LOGIN_EVENT,
                    AdminAuditConstants.FAILURE_OUTCOME)
                    .resourceType(AdminAuditConstants.ADMIN_ACCOUNT_RESOURCE)
                    .reasonCode(reasonCode)
                    .additionalFields(additionalFields)
                    .build());
            log.warn(
                    "event=admin_login_failed principalFingerprint={} reasonCode={}",
                    principalFingerprint,
                    reasonCode);
            throw ex;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logout(String username) {
        adminAuthenticationSecurityService.logout(username);
        log.info("event=admin_logout_completed principalFingerprint={}", principalFingerprint(username));
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Hashes the login principal for operational logs.
     */
    private String principalFingerprint(String username) {
        return securityAuditLogger.hashValue(username);
    }
}
