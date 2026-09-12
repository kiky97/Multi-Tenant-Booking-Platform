package com.booking.engine.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class AdminCookieCsrfHeaderFilterTest {

    private AdminAuthCookieService adminAuthCookieService;
    private JwtService jwtService;
    private AdminCookieCsrfHeaderFilter filter;

    @BeforeEach
    void setUp() {
        adminAuthCookieService = Mockito.mock(AdminAuthCookieService.class);
        jwtService = Mockito.mock(JwtService.class);
        filter = new AdminCookieCsrfHeaderFilter(adminAuthCookieService, jwtService, Mockito.mock(JsonMapper.class));
    }

    @Test
    void rejectsUnsafeAdminRequestAuthenticatedByCookieWithoutCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        when(adminAuthCookieService.resolveTokenFromCookie(request)).thenReturn("cookie-jwt");

        filter.doFilter(request, response, chain);

        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsUnsafeAdminRequestAuthenticatedByCookieWithWrongCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/auth/logout");
        request.addHeader(AdminCookieCsrfHeaderFilter.ADMIN_CSRF_HEADER, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        when(adminAuthCookieService.resolveTokenFromCookie(request)).thenReturn("cookie-jwt");
        when(jwtService.extractCsrfToken("cookie-jwt")).thenReturn("correct-token");

        filter.doFilter(request, response, chain);

        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void allowsUnsafeAdminCookieRequestWithMatchingCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/auth/logout");
        request.addHeader(AdminCookieCsrfHeaderFilter.ADMIN_CSRF_HEADER, "correct-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        when(adminAuthCookieService.resolveTokenFromCookie(request)).thenReturn("cookie-jwt");
        when(jwtService.extractCsrfToken("cookie-jwt")).thenReturn("correct-token");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void allowsUnsafeAdminBearerRequestWithoutCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/auth/logout");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void allowsSafeAdminCookieRequestWithoutCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/auth/session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        when(adminAuthCookieService.resolveTokenFromCookie(request)).thenReturn("cookie-jwt");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }
}
