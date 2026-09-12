package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.AdminRole;
import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.properties.AuthSecurityProperties;
import com.booking.engine.repository.AdminUserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthenticationSecurityServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    @Spy
    private AuthSecurityProperties authSecurityProperties = new AuthSecurityProperties();

    @InjectMocks
    private AdminAuthenticationSecurityService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(securityAuditLogger.event(anyString(), anyString()))
                .thenAnswer(invocation -> SecurityAuditEvent.builder()
                        .eventType(invocation.getArgument(0))
                        .outcome(invocation.getArgument(1)));
        org.mockito.Mockito.lenient().when(securityAuditLogger.hashValue(anyString())).thenReturn("fingerprint");
    }

    @Test
    void registerFailedLoginIncrementsAttemptsAndStoresFailureTimestamp() {
        AdminUserEntity admin = activeAdmin();
        admin.setFailedLoginAttempts(1);
        when(adminUserRepository.findByUsernameAndActiveTrueForUpdate("admin")).thenReturn(Optional.of(admin));

        service.registerFailedLogin("admin");

        assertThat(admin.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(admin.getLastFailedLoginAt()).isNotNull();
        assertThat(admin.getLockedUntil()).isNull();
    }

    @Test
    void registerFailedLoginLocksAccountAfterConfiguredThreshold() {
        authSecurityProperties.setMaxFailedAttempts(3);
        authSecurityProperties.setLockDurationSeconds(600);
        AdminUserEntity admin = activeAdmin();
        admin.setFailedLoginAttempts(2);
        when(adminUserRepository.findByUsernameAndActiveTrueForUpdate("admin")).thenReturn(Optional.of(admin));
        service.registerFailedLogin("admin");

        assertThat(admin.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(admin.getLockedUntil()).isNotNull();
        assertThat(admin.getLockedUntil()).isAfter(admin.getLastFailedLoginAt());
        assertThat(admin.getTokenVersion()).isEqualTo(1);
        org.mockito.Mockito.verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "AUTH_ACCOUNT_LOCK".equals(event.getEventType())
                        && "FAILED_LOGIN_THRESHOLD_REACHED".equals(event.getReasonCode())
                        && event.getActorUsername() == null
                        && "fingerprint".equals(event.getAdditionalFields().get("principalFingerprint"))));
    }

    @Test
    void registerFailedLoginResetsExpiredLockBeforeCountingNewFailure() {
        authSecurityProperties.setMaxFailedAttempts(3);
        AdminUserEntity admin = activeAdmin();
        admin.setFailedLoginAttempts(3);
        admin.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(adminUserRepository.findByUsernameAndActiveTrueForUpdate("admin")).thenReturn(Optional.of(admin));

        service.registerFailedLogin("admin");

        assertThat(admin.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(admin.getLockedUntil()).isNull();
        assertThat(admin.getLastFailedLoginAt()).isNotNull();
    }

    @Test
    void registerSuccessfulLoginClearsFailedAttemptState() {
        AdminUserEntity admin = activeAdmin();
        admin.setFailedLoginAttempts(4);
        admin.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        admin.setLastFailedLoginAt(LocalDateTime.now().minusMinutes(1));
        when(adminUserRepository.findByUsernameAndActiveTrueForUpdate("admin")).thenReturn(Optional.of(admin));

        service.registerSuccessfulLogin("admin");

        assertThat(admin.getFailedLoginAttempts()).isZero();
        assertThat(admin.getLockedUntil()).isNull();
        assertThat(admin.getLastFailedLoginAt()).isNull();
        verify(adminUserRepository).findByUsernameAndActiveTrueForUpdate("admin");
    }

    @Test
    void logoutShouldIncrementTokenVersion() {
        AdminUserEntity admin = activeAdmin();
        admin.setTokenVersion(2);
        when(adminUserRepository.findByUsernameAndActiveTrueForUpdate("admin")).thenReturn(Optional.of(admin));
        service.logout("admin");

        assertThat(admin.getTokenVersion()).isEqualTo(3);
        org.mockito.Mockito.verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "AUTH_LOGOUT".equals(event.getEventType())
                        && "LOGOUT".equals(event.getReasonCode())));
    }

    private AdminUserEntity activeAdmin() {
        return AdminUserEntity.builder()
                .username("admin")
                .passwordHash("$2a$10$hash")
                .role(AdminRole.ADMIN)
                .active(true)
                .build();
    }
}
