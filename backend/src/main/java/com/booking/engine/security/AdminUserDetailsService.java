package com.booking.engine.security;

import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.repository.AdminUserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * UserDetailsService for administrative JWT authentication.
 */
@Component
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUserEntity user = findActiveAdminOrThrow(username);

        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .accountLocked(user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .build();
    }

    public int loadTokenVersionByUsername(String username) {
        return findActiveAdminOrThrow(username).getTokenVersion();
    }

    private AdminUserEntity findActiveAdminOrThrow(String username) {
        return adminUserRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new UsernameNotFoundException("Authentication failed"));
    }
}
