package com.booking.engine.service;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;

/**
 * Service contract for employee schedule period operations.
 * Defines employee schedule period related business operations.
 */
public interface EmployeeSchedulePeriodService {

    /**
     * Retrieves the latest saved employee schedule period settings.
     *
     * @return persisted period settings
     */
    EmployeeSchedulePeriodResponseDto getPeriodSettings();

    /**
     * Creates or updates employee schedule data for a date period.
     *
     * @return persisted period settings after update
     */
    EmployeeSchedulePeriodResponseDto upsertPeriod(EmployeeSchedulePeriodRequestDto request);
}
