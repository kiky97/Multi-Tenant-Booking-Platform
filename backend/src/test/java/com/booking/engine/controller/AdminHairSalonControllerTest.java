package com.booking.engine.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.service.HairSalonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminHairSalonControllerTest {

    private MockMvc mockMvc;
    private HairSalonService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        service = org.mockito.Mockito.mock(HairSalonService.class);
        AdminHairSalonController controller = new AdminHairSalonController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mapper = new ObjectMapper();
    }

    @Test
    void updateReturns204() throws Exception {
        HairSalonRequestDto req = HairSalonRequestDto.builder().name("Salon").address("Addr").build();
        mockMvc.perform(put("/api/v1/admin/hair-salon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
        verify(service).updateHairSalonData(req);
    }
}
