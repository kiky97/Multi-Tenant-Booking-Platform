package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

class SecurityAuditLoggerTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void eventUsesCurrentRequestAndAuthenticatedActor() {
        JsonMapper jsonMapper = Mockito.mock(JsonMapper.class);
        ClientIpResolver clientIpResolver = Mockito.mock(ClientIpResolver.class);
        SecurityAuditLogger logger = new SecurityAuditLogger(jsonMapper, clientIpResolver);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/auth/logout");
        request.setRemoteAddr("198.51.100.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Mockito.when(clientIpResolver.resolve(request)).thenReturn("198.51.100.10");
        User adminUser = (User) User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                adminUser,
                null,
                adminUser.getAuthorities()));

        SecurityAuditEvent event = logger.event("AUTH_LOGOUT", "SUCCESS").build();

        assertThat(event.getEventType()).isEqualTo("AUTH_LOGOUT");
        assertThat(event.getOutcome()).isEqualTo("SUCCESS");
        assertThat(event.getActorUsername()).isEqualTo("admin");
        assertThat(event.getClientIp()).isEqualTo("198.51.100.10");
        assertThat(event.getMethod()).isEqualTo("POST");
        assertThat(event.getPath()).isEqualTo("/api/v1/admin/auth/logout");
    }

    @Test
    void maskingAndHashingSanitizeSensitiveIdentifiers() {
        JsonMapper jsonMapper = Mockito.mock(JsonMapper.class);
        ClientIpResolver clientIpResolver = Mockito.mock(ClientIpResolver.class);
        SecurityAuditLogger logger = new SecurityAuditLogger(jsonMapper, clientIpResolver);

        assertThat(logger.maskEmail("john.doe@example.com")).isEqualTo("j***e@example.com");
        assertThat(logger.hashValue(" +353 87 123 4567 ")).isEqualTo(logger.hashValue("+353 87 123 4567"));
        assertThat(logger.hashValue(" +353 87 123 4567 ")).hasSize(12);
    }
}
