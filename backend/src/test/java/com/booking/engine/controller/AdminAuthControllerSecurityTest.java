package com.booking.engine.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.config.SecurityConfig;
import com.booking.engine.properties.JwtProperties;
import com.booking.engine.security.AdminAuthCookieService;
import com.booking.engine.security.AdminUserDetailsService;
import com.booking.engine.security.JwtAuthenticationFilter;
import com.booking.engine.security.JwtService;
import com.booking.engine.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminAuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminAuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AdminAuthCookieService adminAuthCookieService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    void sessionRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void sessionAllowsAuthenticatedAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auth/session"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void logoutAllowsAuthenticatedAdmin() throws Exception {
        when(adminAuthCookieService.clearSessionCookie())
                .thenReturn(ResponseCookie.from("admin_access_token", "").path("/api/v1/admin").maxAge(0).build());

        mockMvc.perform(post("/api/v1/admin/auth/logout"))
                .andExpect(status().isNoContent());

        verify(authService).logout("admin");
    }
}
