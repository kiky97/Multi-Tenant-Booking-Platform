package com.booking.engine.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.service.HairSalonService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HairSalonControllerTest {

    private MockMvc mockMvc;

    @Mock
    private HairSalonService hairSalonService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        HairSalonController controller = new HairSalonController(hairSalonService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getHairSalonReturnsData() throws Exception {
        HairSalonResponseDto response = HairSalonResponseDto.builder()
                .id(UUID.randomUUID())
                .name("Salon")
                .address("Street 1")
                .email("info@example.com")
                .build();
        when(hairSalonService.getHairSalonData()).thenReturn(response);

        mockMvc.perform(get("/api/v1/public/hair-salon").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Salon")))
                .andExpect(jsonPath("$.email", is("info@example.com")));
    }
}
