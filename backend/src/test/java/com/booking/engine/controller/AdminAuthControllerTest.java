package com.booking.engine.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.properties.AuthSecurityProperties;
import com.booking.engine.properties.JwtProperties;
import com.booking.engine.security.AdminAuthCookieService;
import com.booking.engine.security.JwtService;
import com.booking.engine.service.AuthService;
import jakarta.servlet.http.Cookie;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminAuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private JwtProperties jwtProperties;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        authService = org.mockito.Mockito.mock(AuthService.class);
        jwtProperties = Mockito.mock(JwtProperties.class);
        jwtService = Mockito.mock(JwtService.class);
        when(jwtProperties.getExpirationSeconds()).thenReturn(3600L);
        AdminAuthCookieService adminAuthCookieService =
                new AdminAuthCookieService(new AuthSecurityProperties(), jwtProperties);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminAuthController(authService, adminAuthCookieService, jwtProperties, jwtService)).build();
    }

    @Test
    void sessionReturnsCurrentAdminMetadata() throws Exception {
        Principal principal = () -> "admin";

        mockMvc.perform(get("/api/v1/admin/auth/session").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }

    @Test
    void sessionReturnsCsrfTokenForCookieBackedSession() throws Exception {
        Principal principal = () -> "admin";
        when(jwtService.extractCsrfToken("jwt")).thenReturn("csrf-token");

        mockMvc.perform(get("/api/v1/admin/auth/session")
                        .principal(principal)
                        .cookie(new Cookie("admin_access_token", "jwt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").value("csrf-token"));
    }

    @Test
    void logoutReturnsNoContentAndDelegatesToService() throws Exception {
        Principal principal = () -> "admin";

        mockMvc.perform(post("/api/v1/admin/auth/logout").principal(principal))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Set-Cookie", containsString("Max-Age=0")));

        verify(authService).logout("admin");
    }
}
