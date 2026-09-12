package com.booking.engine.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.service.TreatmentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TreatmentControllerTest {

    private MockMvc mockMvc;
    private TreatmentService service;

    @BeforeEach
    void setup() {
        service = org.mockito.Mockito.mock(TreatmentService.class);
        TreatmentController controller = new TreatmentController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getAllReturns200() throws Exception {
        when(service.getAllTreatments()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/public/treatments"))
                .andExpect(status().isOk());
    }

    @Test
    void getByIdReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getTreatmentById(id)).thenReturn(TreatmentResponseDto.builder().id(id).build());
        mockMvc.perform(get("/api/v1/public/treatments/{id}", id))
                .andExpect(status().isOk());
    }
}
