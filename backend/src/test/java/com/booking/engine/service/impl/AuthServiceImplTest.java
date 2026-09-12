package com.booking.engine.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.LoginRequestDto;
import com.booking.engine.dto.LoginResponseDto;
import com.booking.engine.properties.JwtProperties;
import com.booking.engine.security.AdminAuthenticationSecurityService;
import com.booking.engine.security.AdminUserDetailsService;
import com.booking.engine.security.JwtService;
import com.booking.engine.security.SecurityAuditEvent;
import com.booking.engine.security.SecurityAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private AdminAuthenticationSecurityService adminAuthenticationSecurityService;

    @Mock
    private AdminUserDetailsService adminUserDetailsService;

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(securityAuditLogger.event(anyString(), anyString()))
                .thenAnswer(invocation -> SecurityAuditEvent.builder()
                        .eventType(invocation.getArgument(0))
                        .outcome(invocation.getArgument(1)));
        org.mockito.Mockito.lenient().when(securityAuditLogger.hashValue(anyString())).thenReturn("fingerprint");
    }

    @Test
    void loginReturnsJwtResponse() {
        LoginRequestDto request = LoginRequestDto.builder()
                .username("admin")
                .password("password")
                .build();
        UserDetails principal = User.withUsername("admin").password("pwd").authorities("ROLE_ADMIN").build();
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(adminUserDetailsService.loadTokenVersionByUsername("admin")).thenReturn(4);
        when(jwtProperties.getExpirationSeconds()).thenReturn(3600L);
        when(jwtService.generateCsrfToken()).thenReturn("csrf-token");
        when(jwtService.generateToken(any(UserDetails.class), any(), org.mockito.ArgumentMatchers.eq(4)))
                .thenReturn("jwt-token");
        LoginResponseDto response = authService.login(request);

        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getCsrfToken()).isEqualTo("csrf-token");
        assertThat(response.getExpiresInSeconds()).isEqualTo(3600L);
        verify(adminAuthenticationSecurityService).registerSuccessfulLogin("admin");
        verify(jwtService).generateToken(
                any(UserDetails.class),
                org.mockito.ArgumentMatchers
                        .argThat(claims -> "csrf-token".equals(claims.get(JwtService.CSRF_TOKEN_CLAIM))),
                org.mockito.ArgumentMatchers.eq(4));
        verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "AUTH_LOGIN".equals(event.getEventType())
                        && "SUCCESS".equals(event.getOutcome())
                        && "admin".equals(event.getActorUsername())));
    }

    @Test
    void loginTracksFailedAttemptWhenCredentialsAreInvalid() {
        LoginRequestDto request = LoginRequestDto.builder()
                .username("admin")
                .password("wrong-password")
                .build();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(adminAuthenticationSecurityService).registerFailedLogin("admin");
        verify(adminAuthenticationSecurityService, never()).registerSuccessfulLogin("admin");
        verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "AUTH_LOGIN".equals(event.getEventType())
                        && "FAILURE".equals(event.getOutcome())
                        && "INVALID_CREDENTIALS".equals(event.getReasonCode())
                        && event.getActorUsername() == null
                        && "fingerprint".equals(event.getAdditionalFields().get("principalFingerprint"))));
    }

    @Test
    void loginDoesNotIncrementFailuresWhenAccountIsAlreadyLocked() {
        LoginRequestDto request = LoginRequestDto.builder()
                .username("admin")
                .password("password")
                .build();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("User account is locked"));
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(LockedException.class);

        verify(adminAuthenticationSecurityService, never()).registerFailedLogin("admin");
        verify(adminAuthenticationSecurityService, never()).registerSuccessfulLogin("admin");
        verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "ACCOUNT_LOCKED".equals(event.getReasonCode())
                        && event.getActorUsername() == null
                        && "fingerprint".equals(event.getAdditionalFields().get("principalFingerprint"))));
    }

    @Test
    void logoutDelegatesToAuthenticationSecurityService() {
        authService.logout("admin");

        verify(adminAuthenticationSecurityService).logout("admin");
    }
}
