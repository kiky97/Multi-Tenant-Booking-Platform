package com.booking.engine.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import com.booking.engine.service.EmployeeScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmployeeScheduleAdminControllerTest {

    private MockMvc mockMvc;
    private EmployeeScheduleService service;
    private ObjectMapper objectMapper;
    private UUID employeeId;

    @BeforeEach
    void setup() {
        service = org.mockito.Mockito.mock(EmployeeScheduleService.class);
        EmployeeScheduleAdminController controller = new EmployeeScheduleAdminController(service);
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
        employeeId = UUID.randomUUID();
    }

    @Test
    void getScheduleReturns200() throws Exception {
        EmployeeScheduleResponseDto dto = EmployeeScheduleResponseDto.builder()
                .id(UUID.randomUUID())
                .workingDate(LocalDate.now())
                .workingDay(true)
                .build();
        when(service.getSchedule(employeeId, null, null)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/admin/employees/{id}/schedule", employeeId))
                .andExpect(status().isOk());
    }

    @Test
    void upsertDayReturns204() throws Exception {
        EmployeeScheduleRequestDto req = EmployeeScheduleRequestDto.builder()
                .workingDate(LocalDate.now())
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .breakStartTime(LocalTime.of(13, 0))
                .breakEndTime(LocalTime.of(14, 0))
                .build();

        mockMvc.perform(put("/api/v1/admin/employees/{id}/schedule", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    void upsertDayReturns204WhenBreakIsOmitted() throws Exception {
        EmployeeScheduleRequestDto req = EmployeeScheduleRequestDto.builder()
                .workingDate(LocalDate.now())
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .breakStartTime(null)
                .breakEndTime(null)
                .build();

        mockMvc.perform(put("/api/v1/admin/employees/{id}/schedule", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }
}
