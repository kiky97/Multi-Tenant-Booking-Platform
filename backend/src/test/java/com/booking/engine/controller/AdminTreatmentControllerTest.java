package com.booking.engine.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.ReorderRequestDto;
import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.service.TreatmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminTreatmentControllerTest {

    private MockMvc mockMvc;
    private TreatmentService treatmentService;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        treatmentService = org.mockito.Mockito.mock(TreatmentService.class);
        AdminTreatmentController controller = new AdminTreatmentController(treatmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mapper = new ObjectMapper();
    }

    @Test
    void createReturns201() throws Exception {
        TreatmentRequestDto req = TreatmentRequestDto.builder()
                .name("Cut")
                .durationMinutes(30)
                .price(new java.math.BigDecimal("20.00"))
                .description("Clean finish with premium detailing.")
                .build();
        TreatmentResponseDto resp = TreatmentResponseDto.builder().id(UUID.randomUUID()).build();
        when(treatmentService.createTreatment(req)).thenReturn(resp);

        mockMvc.perform(post("/api/v1/admin/treatments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void updateReturns204() throws Exception {
        TreatmentRequestDto req = TreatmentRequestDto.builder()
                .name("Cut")
                .durationMinutes(30)
                .price(new java.math.BigDecimal("20.00"))
                .description("Clean finish with premium detailing.")
                .build();
        UUID id = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/admin/treatments/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
        verify(treatmentService).updateTreatment(id, req);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/admin/treatments/{id}", id))
                .andExpect(status().isNoContent());
        verify(treatmentService).removeTreatment(id);
    }

    @Test
    void reorderReturns204() throws Exception {
        ReorderRequestDto req = new ReorderRequestDto(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/admin/treatments/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
        verify(treatmentService).reorderTreatments(req.getId1(), req.getId2());
    }
}
