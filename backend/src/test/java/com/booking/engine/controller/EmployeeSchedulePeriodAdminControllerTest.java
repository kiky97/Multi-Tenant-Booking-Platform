package com.booking.engine.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.EmployeeSchedulePeriodDayRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;
import com.booking.engine.exception.GlobalExceptionHandler;
import com.booking.engine.service.EmployeeScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmployeeSchedulePeriodAdminControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private EmployeeScheduleService service;

    @BeforeEach
    void setup() {
        service = org.mockito.Mockito.mock(EmployeeScheduleService.class);
        EmployeeSchedulePeriodAdminController controller = new EmployeeSchedulePeriodAdminController(service);
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void getPeriodReturns200() throws Exception {
        when(service.getPeriodSettings()).thenReturn(EmployeeSchedulePeriodResponseDto.builder()
                .startDate(LocalDate.of(2026, 4, 3))
                .endDate(LocalDate.of(2026, 4, 9))
                .applyToAllEmployees(true)
                .days(List.of())
                .build());

        mockMvc.perform(get("/api/v1/admin/employees/schedule/period"))
                .andExpect(status().isOk());
    }

    @Test
    void upsertPeriodReturns200() throws Exception {
        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 3))
                .endDate(LocalDate.of(2026, 4, 9))
                .applyToAllEmployees(true)
                .days(Arrays.stream(DayOfWeek.values())
                        .map(day -> EmployeeSchedulePeriodDayRequestDto.builder()
                                .dayOfWeek(day)
                                .workingDay(true)
                                .openTime(LocalTime.of(9, 0))
                                .closeTime(LocalTime.of(18, 0))
                                .breakStartTime(LocalTime.of(13, 0))
                                .breakEndTime(LocalTime.of(14, 0))
                                .build())
                        .toList())
                .build();

        when(service.upsertPeriod(org.mockito.ArgumentMatchers.any())).thenReturn(EmployeeSchedulePeriodResponseDto.builder()
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .applyToAllEmployees(request.getApplyToAllEmployees())
                .days(request.getDays())
                .build());

        mockMvc.perform(put("/api/v1/admin/employees/schedule/period")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void upsertPeriodReturns200WhenBreakIsOmitted() throws Exception {
        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 3))
                .endDate(LocalDate.of(2026, 4, 9))
                .applyToAllEmployees(true)
                .days(Arrays.stream(DayOfWeek.values())
                        .map(day -> EmployeeSchedulePeriodDayRequestDto.builder()
                                .dayOfWeek(day)
                                .workingDay(true)
                                .openTime(LocalTime.of(9, 0))
                                .closeTime(LocalTime.of(18, 0))
                                .breakStartTime(null)
                                .breakEndTime(null)
                                .build())
                        .toList())
                .build();

        when(service.upsertPeriod(org.mockito.ArgumentMatchers.any())).thenReturn(EmployeeSchedulePeriodResponseDto.builder()
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .applyToAllEmployees(request.getApplyToAllEmployees())
                .days(request.getDays())
                .build());

        mockMvc.perform(put("/api/v1/admin/employees/schedule/period")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void upsertPeriodReturnsValidationErrorWhenEndDateMissing() throws Exception {
        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 3))
                .endDate(null)
                .applyToAllEmployees(true)
                .days(Arrays.stream(DayOfWeek.values())
                        .map(day -> EmployeeSchedulePeriodDayRequestDto.builder()
                                .dayOfWeek(day)
                                .workingDay(true)
                                .openTime(LocalTime.of(9, 0))
                                .closeTime(LocalTime.of(18, 0))
                                .breakStartTime(LocalTime.of(13, 0))
                                .breakEndTime(LocalTime.of(14, 0))
                                .build())
                        .toList())
                .build();

        mockMvc.perform(put("/api/v1/admin/employees/schedule/period")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").value("End date is required"));
    }

    @Test
    void upsertPeriodReturnsValidationErrorWhenEndDateIsBlankString() throws Exception {
        String request = """
                {
                  "startDate": "2026-04-05",
                  "endDate": "",
                  "employeeId": null,
                  "applyToAllEmployees": true,
                  "days": [
                    {"dayOfWeek":"MONDAY","workingDay":true,"openTime":"09:00","closeTime":"17:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"TUESDAY","workingDay":true,"openTime":"09:00","closeTime":"18:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"WEDNESDAY","workingDay":true,"openTime":"09:00","closeTime":"18:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"THURSDAY","workingDay":true,"openTime":"09:00","closeTime":"18:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"FRIDAY","workingDay":true,"openTime":"09:00","closeTime":"18:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"SATURDAY","workingDay":true,"openTime":"09:00","closeTime":"16:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"SUNDAY","workingDay":false,"openTime":null,"closeTime":null,"breakStartTime":null,"breakEndTime":null}
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/admin/employees/schedule/period")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").value("End date is required"));
    }

    @Test
    void upsertPeriodReturnsBadRequestWhenEndDateIsNotRealCalendarDate() throws Exception {
        String request = """
                {
                  "startDate": "2026-04-05",
                  "endDate": "2026-04-31",
                  "employeeId": null,
                  "applyToAllEmployees": true,
                  "days": [
                    {"dayOfWeek":"MONDAY","workingDay":true,"openTime":"09:00","closeTime":"17:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"TUESDAY","workingDay":true,"openTime":"09:00","closeTime":"18:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"WEDNESDAY","workingDay":true,"openTime":"09:00","closeTime":"18:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"THURSDAY","workingDay":true,"openTime":"09:00","closeTime":"18:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"FRIDAY","workingDay":true,"openTime":"09:00","closeTime":"18:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"SATURDAY","workingDay":true,"openTime":"09:00","closeTime":"16:00","breakStartTime":"13:00","breakEndTime":"14:00"},
                    {"dayOfWeek":"SUNDAY","workingDay":false,"openTime":null,"closeTime":null,"breakStartTime":null,"breakEndTime":null}
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/admin/employees/schedule/period")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please choose a real calendar date for End date."));
    }
}
