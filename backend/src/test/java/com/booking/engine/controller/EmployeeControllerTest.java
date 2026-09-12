package com.booking.engine.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.service.EmployeeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmployeeControllerTest {

    private MockMvc mockMvc;
    private EmployeeService service;

    @BeforeEach
    void setup() {
        service = org.mockito.Mockito.mock(EmployeeService.class);
        EmployeeController controller = new EmployeeController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getAllReturns200() throws Exception {
        when(service.getAllEmployees()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/public/employees"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllBookableReturns200() throws Exception {
        when(service.getBookableEmployees()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/public/employees").param("bookable", "true"))
                .andExpect(status().isOk());

        verify(service).getBookableEmployees();
    }

    @Test
    void getByIdReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getEmployeeById(id)).thenReturn(EmployeeResponseDto.builder().id(id).build());
        mockMvc.perform(get("/api/v1/public/employees/{id}", id))
                .andExpect(status().isOk());
    }
}
