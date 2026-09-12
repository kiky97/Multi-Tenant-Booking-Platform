package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.AdminRole;
import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.repository.AdminUserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AdminUserDetailsServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @InjectMocks
    private AdminUserDetailsService service;

    @Test
    void loadUserByUsernameReturnsSpringSecurityUserForActiveAdmin() {
        AdminUserEntity admin = AdminUserEntity.builder()
                .username("admin")
                .passwordHash("$2a$10$hash")
                .role(AdminRole.ADMIN)
                .tokenVersion(6)
                .active(true)
                .build();
        when(adminUserRepository.findByUsernameAndActiveTrue("admin")).thenReturn(Optional.of(admin));

        UserDetails userDetails = service.loadUserByUsername("admin");

        assertThat(userDetails.getUsername()).isEqualTo("admin");
        assertThat(userDetails.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(service.loadTokenVersionByUsername("admin")).isEqualTo(6);
    }

    @Test
    void loadUserByUsernameMarksLockedAdminAsLocked() {
        AdminUserEntity admin = AdminUserEntity.builder()
                .username("admin")
                .passwordHash("$2a$10$hash")
                .role(AdminRole.ADMIN)
                .lockedUntil(LocalDateTime.now().plusMinutes(15))
                .active(true)
                .build();
        when(adminUserRepository.findByUsernameAndActiveTrue("admin")).thenReturn(Optional.of(admin));

        UserDetails userDetails = service.loadUserByUsername("admin");

        assertThat(userDetails.isAccountNonLocked()).isFalse();
    }

    @Test
    void loadUserByUsernameThrowsWhenAdminDoesNotExist() {
        when(adminUserRepository.findByUsernameAndActiveTrue("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Authentication failed");
    }
}
