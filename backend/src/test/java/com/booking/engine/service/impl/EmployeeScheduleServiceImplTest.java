package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;
import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import com.booking.engine.service.EmployeeScheduleDayService;
import com.booking.engine.service.EmployeeSchedulePeriodService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeScheduleServiceImplTest {

    @Mock
    private EmployeeScheduleDayService employeeScheduleDayService;

    @Mock
    private EmployeeSchedulePeriodService employeeSchedulePeriodService;

    private EmployeeScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmployeeScheduleServiceImpl(employeeScheduleDayService, employeeSchedulePeriodService);
    }

    @Test
    void getScheduleDelegatesToDayService() {
        UUID employeeId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 3);
        List<EmployeeScheduleResponseDto> expected = List.of(EmployeeScheduleResponseDto.builder().build());

        when(employeeScheduleDayService.getSchedule(employeeId, from, to)).thenReturn(expected);

        assertEquals(expected, service.getSchedule(employeeId, from, to));
        verify(employeeScheduleDayService).getSchedule(employeeId, from, to);
    }

    @Test
    void upsertDayDelegatesToDayService() {
        UUID employeeId = UUID.randomUUID();
        EmployeeScheduleRequestDto request = EmployeeScheduleRequestDto.builder()
                .workingDate(LocalDate.of(2026, 4, 23))
                .workingDay(true)
                .build();

        service.upsertDay(employeeId, request);

        verify(employeeScheduleDayService).upsertDay(employeeId, request);
    }

    @Test
    void getPeriodSettingsDelegatesToPeriodService() {
        EmployeeSchedulePeriodResponseDto expected = EmployeeSchedulePeriodResponseDto.builder().build();
        when(employeeSchedulePeriodService.getPeriodSettings()).thenReturn(expected);

        assertEquals(expected, service.getPeriodSettings());
        verify(employeeSchedulePeriodService).getPeriodSettings();
    }

    @Test
    void upsertPeriodDelegatesToPeriodService() {
        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder().build();
        EmployeeSchedulePeriodResponseDto expected = EmployeeSchedulePeriodResponseDto.builder().build();
        when(employeeSchedulePeriodService.upsertPeriod(request)).thenReturn(expected);

        assertEquals(expected, service.upsertPeriod(request));
        verify(employeeSchedulePeriodService).upsertPeriod(request);
    }
}
