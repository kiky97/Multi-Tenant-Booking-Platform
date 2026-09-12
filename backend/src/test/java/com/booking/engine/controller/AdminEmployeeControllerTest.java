package com.booking.engine.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.EmployeeRequestDto;
import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.dto.ReorderRequestDto;
import com.booking.engine.service.EmployeeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminEmployeeControllerTest {

    private MockMvc mockMvc;
    private EmployeeService employeeService;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        employeeService = org.mockito.Mockito.mock(EmployeeService.class);
        AdminEmployeeController controller = new AdminEmployeeController(employeeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mapper = new ObjectMapper();
    }

    @Test
    void createReturns201() throws Exception {
        EmployeeRequestDto req = EmployeeRequestDto.builder()
                .name("B1")
                .role("Senior Employee")
                .displayOrder(0)
                .build();
        when(employeeService.createEmployee(req)).thenReturn(EmployeeResponseDto.builder().id(UUID.randomUUID()).build());

        mockMvc.perform(post("/api/v1/admin/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void updateReturns204() throws Exception {
        EmployeeRequestDto req = EmployeeRequestDto.builder()
                .name("B1")
                .role("Senior Employee")
                .displayOrder(0)
                .build();
        UUID id = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/admin/employees/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
        verify(employeeService).updateEmployee(id, req);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/admin/employees/{id}", id))
                .andExpect(status().isNoContent());
        verify(employeeService).deleteEmployee(id);
    }

    @Test
    void reorderReturns204() throws Exception {
        ReorderRequestDto req = new ReorderRequestDto(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/admin/employees/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
        verify(employeeService).reorderEmployees(req.getId1(), req.getId2());
    }
}
