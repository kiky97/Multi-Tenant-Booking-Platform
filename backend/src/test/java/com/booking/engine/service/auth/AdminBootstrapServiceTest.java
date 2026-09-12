package com.booking.engine.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.AdminRole;
import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.properties.AdminBootstrapProperties;
import com.booking.engine.repository.AdminUserRepository;
import com.booking.engine.security.AdminPasswordPolicyValidator;
import com.booking.engine.security.SecurityAuditEvent;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.service.AdminBootstrapPolicy;
import com.booking.engine.service.AdminBootstrapService;
import com.booking.engine.service.impl.AdminBootstrapPolicyImpl;
import com.booking.engine.service.impl.AdminBootstrapServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock
    private AdminBootstrapProperties properties;

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AdminPasswordPolicyValidator adminPasswordPolicyValidator;

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        AdminBootstrapPolicy policy = new AdminBootstrapPolicyImpl(adminPasswordPolicyValidator);
        service = new AdminBootstrapServiceImpl(
                adminUserRepository,
                properties,
                passwordEncoder,
                securityAuditLogger,
                policy);

        org.mockito.Mockito.lenient().when(adminUserRepository.save(any(AdminUserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(securityAuditLogger.event(anyString(), anyString()))
                .thenAnswer(invocation -> SecurityAuditEvent.builder()
                        .eventType(invocation.getArgument(0))
                        .outcome(invocation.getArgument(1)));
    }

    @Test
    void bootstrapIfEnabledDoesNothingWhenBootstrapIsDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        service.bootstrapIfEnabled();

        verify(adminUserRepository, never()).findByUsername(any());
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void bootstrapIfEnabledFailsFastWhenRequiredCredentialsAreMissing() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getUsername()).thenReturn("   ");
        when(properties.getPassword()).thenReturn(" ");

        assertThatThrownBy(() -> service.bootstrapIfEnabled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ADMIN_BOOTSTRAP_USERNAME")
                .hasMessageContaining("APP_ADMIN_BOOTSTRAP_PASSWORD");
    }

    @Test
    void bootstrapIfEnabledCreatesAdminUserWhenItDoesNotExist() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getUsername()).thenReturn(" admin ");
        when(properties.getPassword()).thenReturn("StrongPass123!");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(adminUserRepository.existsByActiveTrue()).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123!")).thenReturn("hashed-secret");

        service.bootstrapIfEnabled();

        ArgumentCaptor<AdminUserEntity> savedUser = ArgumentCaptor.forClass(AdminUserEntity.class);
        verify(adminUserRepository).save(savedUser.capture());
        verify(adminPasswordPolicyValidator).validate("StrongPass123!");

        assertThat(savedUser.getValue().getUsername()).isEqualTo("admin");
        assertThat(savedUser.getValue().getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(savedUser.getValue().getActive()).isTrue();
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-secret");
        verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "CREATED".equals(event.getReasonCode())));
    }

    @Test
    void bootstrapIfEnabledUpdatesExistingUserWhenRoleStatusOrPasswordNeedCorrection() {
        AdminUserEntity existingUser = AdminUserEntity.builder()
                .username("admin")
                .role(null)
                .active(false)
                .passwordHash("old-hash")
                .build();
        existingUser.setId(UUID.randomUUID());

        when(properties.isEnabled()).thenReturn(true);
        when(properties.isAllowPasswordOverwrite()).thenReturn(true);
        when(properties.getUsername()).thenReturn("admin");
        when(properties.getPassword()).thenReturn("NewStrongPass123!");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("NewStrongPass123!", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewStrongPass123!")).thenReturn("new-hash");

        service.bootstrapIfEnabled();

        verify(adminUserRepository).save(existingUser);
        assertThat(existingUser.getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(existingUser.getActive()).isTrue();
        assertThat(existingUser.getPasswordHash()).isEqualTo("new-hash");
        assertThat(existingUser.getTokenVersion()).isEqualTo(1);
        verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "PASSWORD_OVERWRITE".equals(event.getReasonCode())));
    }

    @Test
    void bootstrapIfEnabledDoesNotPersistWhenExistingAdminCredentialsAreAlreadyValid() {
        AdminUserEntity existingUser = AdminUserEntity.builder()
                .username("admin")
                .role(AdminRole.ADMIN)
                .active(true)
                .passwordHash("stored-hash")
                .build();
        existingUser.setId(UUID.randomUUID());

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getUsername()).thenReturn("admin");
        when(properties.getPassword()).thenReturn("StrongPass123!");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("StrongPass123!", "stored-hash")).thenReturn(true);

        service.bootstrapIfEnabled();

        verify(adminUserRepository, never()).save(any());
        verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "VERIFIED".equals(event.getReasonCode())));
    }

    @Test
    void bootstrapIfEnabledFailsWhenBootstrapPasswordViolatesPolicy() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getUsername()).thenReturn("admin");
        when(properties.getPassword()).thenReturn("weak");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("weak"))
                .when(adminPasswordPolicyValidator)
                .validate("weak");

        assertThatThrownBy(() -> service.bootstrapIfEnabled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security requirements");

        verify(adminUserRepository, never()).findByUsername(any());
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void bootstrapIfEnabledFailsWhenExistingPasswordWouldBeOverwrittenWithoutExplicitOptIn() {
        AdminUserEntity existingUser = AdminUserEntity.builder()
                .username("admin")
                .role(AdminRole.ADMIN)
                .active(true)
                .passwordHash("old-hash")
                .build();
        existingUser.setId(UUID.randomUUID());

        when(properties.isEnabled()).thenReturn(true);
        when(properties.isAllowPasswordOverwrite()).thenReturn(false);
        when(properties.getUsername()).thenReturn("admin");
        when(properties.getPassword()).thenReturn("DifferentStrong123!");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("DifferentStrong123!", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.bootstrapIfEnabled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE");

        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void bootstrapIfEnabledAllowsExistingPasswordOverwriteWhenExplicitlyEnabled() {
        AdminUserEntity existingUser = AdminUserEntity.builder()
                .username("admin")
                .role(AdminRole.ADMIN)
                .active(true)
                .passwordHash("old-hash")
                .build();
        existingUser.setId(UUID.randomUUID());

        when(properties.isEnabled()).thenReturn(true);
        when(properties.isAllowPasswordOverwrite()).thenReturn(true);
        when(properties.getUsername()).thenReturn("admin");
        when(properties.getPassword()).thenReturn("DifferentStrong123!");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("DifferentStrong123!", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("DifferentStrong123!")).thenReturn("rotated-hash");

        service.bootstrapIfEnabled();

        verify(adminUserRepository).save(existingUser);
        assertThat(existingUser.getPasswordHash()).isEqualTo("rotated-hash");
        assertThat(existingUser.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void bootstrapIfEnabledFailsWhenBootstrapWouldCreateAnotherAdminWhileOneIsAlreadyActive() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getUsername()).thenReturn("new-admin");
        when(properties.getPassword()).thenReturn("StrongPass123!");
        when(adminUserRepository.findByUsername("new-admin")).thenReturn(Optional.empty());
        when(adminUserRepository.existsByActiveTrue()).thenReturn(true);

        assertThatThrownBy(() -> service.bootstrapIfEnabled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("first active admin account");

        verify(adminUserRepository, never()).save(any());
    }
}
