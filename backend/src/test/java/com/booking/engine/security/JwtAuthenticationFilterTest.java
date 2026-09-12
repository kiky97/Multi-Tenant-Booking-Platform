package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private AdminUserDetailsService userDetailsService;

    @Mock
    private AdminAuthCookieService adminAuthCookieService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doesNothingWhenAuthorizationHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatesUserWhenBearerTokenIsValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        UserDetails userDetails = User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(userDetailsService.loadTokenVersionByUsername("admin")).thenReturn(3);
        when(jwtService.isTokenValid("valid-token", userDetails, 3)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatesUserWhenCookieTokenIsValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("admin_access_token", "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        UserDetails userDetails = User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        when(adminAuthCookieService.resolveTokenFromCookie(request)).thenReturn("cookie-token");
        when(jwtService.extractUsername("cookie-token")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(userDetailsService.loadTokenVersionByUsername("admin")).thenReturn(7);
        when(jwtService.isTokenValid("cookie-token", userDetails, 7)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void skipsAuthenticationWhenContextAlreadyContainsAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtService.extractUsername("valid-token")).thenReturn("admin");

        filter.doFilterInternal(request, response, filterChain);

        verify(userDetailsService, never()).loadUserByUsername("admin");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void clearsSecurityContextWhenJwtProcessingFails() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer broken-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtService.extractUsername("broken-token")).thenThrow(new IllegalArgumentException("bad token"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateLockedAdminFromJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        UserDetails lockedUser = User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .accountLocked(true)
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(lockedUser);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).isTokenValid(org.mockito.ArgumentMatchers.eq("valid-token"),
                org.mockito.ArgumentMatchers.eq(lockedUser), org.mockito.ArgumentMatchers.anyInt());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateWhenTokenVersionIsStale() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        UserDetails userDetails = User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(userDetailsService.loadTokenVersionByUsername("admin")).thenReturn(5);
        when(jwtService.isTokenValid("valid-token", userDetails, 5)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
