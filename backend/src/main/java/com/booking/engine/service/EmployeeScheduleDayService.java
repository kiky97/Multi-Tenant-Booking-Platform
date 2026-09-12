package com.booking.engine.service;

import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for employee schedule day operations.
 * Defines employee schedule day related business operations.
 */
public interface EmployeeScheduleDayService {

    /**
     * Retrieves employee schedule entries for a date range.
     *
     * @param employeeId employee identifier
     * @param from       start date, inclusive
     * @param to         end date, inclusive
     * @return schedule entries
     */
    List<EmployeeScheduleResponseDto> getSchedule(UUID employeeId, LocalDate from, LocalDate to);

    /**
     * Creates or updates one employee schedule day.
     *
     * @param employeeId employee identifier
     * @param request    schedule day payload
     */
    void upsertDay(UUID employeeId, EmployeeScheduleRequestDto request);
}
