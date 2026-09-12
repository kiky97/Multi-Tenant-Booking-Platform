package com.booking.engine.service;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;
import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service contract for employee schedule operations.
 * Defines employee schedule related business operations.
 */
public interface EmployeeScheduleService {

    /**
     * Retrieves employee schedule for a date range (inclusive).
     *
     * @param employeeId employee identifier
     * @param from       start date (inclusive), may be null
     * @param to         end date (inclusive), may be null
     * @return list of schedule entries
     */
    @PreAuthorize("hasRole('ADMIN')")
    List<EmployeeScheduleResponseDto> getSchedule(UUID employeeId, LocalDate from, LocalDate to);

    /**
     * Creates or updates schedule data for a single day.
     *
     * @param employeeId employee identifier
     * @param request    schedule payload
     */
    @PreAuthorize("hasRole('ADMIN')")
    void upsertDay(UUID employeeId, EmployeeScheduleRequestDto request);

    /**
     * Returns the latest saved employee schedule period form state.
     *
     * @return persisted form state
     */
    @PreAuthorize("hasRole('ADMIN')")
    EmployeeSchedulePeriodResponseDto getPeriodSettings();

    /**
     * Creates or updates schedule data for every matching day in a date period.
     *
     * @param request bulk schedule payload
     * @return persisted form state after update
     */
    @PreAuthorize("hasRole('ADMIN')")
    EmployeeSchedulePeriodResponseDto upsertPeriod(EmployeeSchedulePeriodRequestDto request);
}
