package com.booking.engine.config;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.controller.AdminHairSalonController;
import com.booking.engine.controller.HairSalonController;
import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.security.AdminAuthCookieService;
import com.booking.engine.security.AdminUserDetailsService;
import com.booking.engine.security.JwtAuthenticationFilter;
import com.booking.engine.security.JwtService;
import com.booking.engine.service.HairSalonService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {HairSalonController.class, AdminHairSalonController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityHeadersWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HairSalonService hairSalonService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AdminAuthCookieService adminAuthCookieService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    void publicResponsesIncludeExplicitSecurityHeaders() throws Exception {
        when(hairSalonService.getHairSalonData()).thenReturn(HairSalonResponseDto.builder()
                .id(UUID.randomUUID())
                .name("Salon")
                .address("Main Street 1")
                .build());

        mockMvc.perform(get("/api/v1/public/hair-salon"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void secureRequestsIncludeHstsHeader() throws Exception {
        when(hairSalonService.getHairSalonData()).thenReturn(HairSalonResponseDto.builder()
                .id(UUID.randomUUID())
                .name("Salon")
                .address("Main Street 1")
                .build());

        mockMvc.perform(get("/api/v1/public/hair-salon").with(secureRequest()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
    }

    @Test
    void adminEndpointStillRequiresAdminRole() throws Exception {
        mockMvc.perform(put("/api/v1/admin/hair-salon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validHairSalonUpdateJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpointAllowsAuthenticatedAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/hair-salon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validHairSalonUpdateJson()))
                .andExpect(status().isNoContent());

        verify(hairSalonService).updateHairSalonData(any());
    }

    private RequestPostProcessor secureRequest() {
        return request -> {
            request.setSecure(true);
            return request;
        };
    }

    private String validHairSalonUpdateJson() {
        return """
                {
                  "name": "Salon",
                  "description": "Updated description",
                  "email": "salon@example.com",
                  "phone": "+353123456",
                  "address": "Main Street 1"
                }
                """;
    }
}
