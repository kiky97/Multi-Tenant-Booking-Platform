package com.booking.engine.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.LoginRequestDto;
import com.booking.engine.dto.LoginResponseDto;
import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.exception.GlobalExceptionHandler;
import com.booking.engine.properties.AuthSecurityProperties;
import com.booking.engine.properties.JwtProperties;
import com.booking.engine.security.AdminAuthCookieService;
import com.booking.engine.security.ClientIpResolver;
import com.booking.engine.security.LoginRateLimitService;
import com.booking.engine.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private LoginRateLimitService loginRateLimitService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(jwtProperties.getExpirationSeconds()).thenReturn(3600L);
        AdminAuthCookieService adminAuthCookieService =
                new AdminAuthCookieService(new AuthSecurityProperties(), jwtProperties);
        AuthController controller = new AuthController(
                authService,
                loginRateLimitService,
                clientIpResolver,
                adminAuthCookieService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(clientIpResolver.resolve(any())).thenReturn("127.0.0.1");
    }

    @Test
    void loginReturnsSessionMetadataAndSetsCookie() throws Exception {
        when(authService.login(any(LoginRequestDto.class))).thenReturn(
                LoginResponseDto.builder()
                        .username("admin")
                        .accessToken("jwt")
                        .csrfToken("csrf-token")
                        .expiresInSeconds(3600L)
                        .build());

        mockMvc.perform(post("/api/v1/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("admin")))
                .andExpect(jsonPath("$.csrfToken", is("csrf-token")))
                .andExpect(jsonPath("$.expiresInSeconds", is(3600)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Set-Cookie", org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("admin_access_token=jwt"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"),
                                org.hamcrest.Matchers.containsString("Path=/api/v1/admin"))));

        verify(loginRateLimitService).registerAttempt("127.0.0.1", "admin");
        verify(loginRateLimitService).resetSuccessfulAttempt("127.0.0.1", "admin");
    }

    @Test
    void loginFailsValidationWhenUsernameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" \",\"password\":\"p\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void loginReturnsNeutralAuthErrorMessage() throws Exception {
        when(authService.login(any(LoginRequestDto.class)))
                .thenThrow(new BadCredentialsException("Admin user not found: admin"));

        mockMvc.perform(post("/api/v1/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid username or password")));

        verify(loginRateLimitService).registerAttempt("127.0.0.1", "admin");
        verify(loginRateLimitService, never()).resetSuccessfulAttempt(eq("127.0.0.1"), eq("admin"));
    }

    @Test
    void loginReturnsTooManyRequestsWhenRateLimitExceeded() throws Exception {
        doThrow(new RateLimitExceededException("too many"))
                .when(loginRateLimitService)
                .registerAttempt("127.0.0.1", "admin");

        mockMvc.perform(post("/api/v1/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error", is("Too Many Requests")))
                .andExpect(jsonPath("$.message", is("Too many login attempts. Please try again later.")));
    }
}
