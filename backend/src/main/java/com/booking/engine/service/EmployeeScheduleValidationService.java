package com.booking.engine.service;

import com.booking.engine.dto.EmployeeSchedulePeriodDayRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Service contract for employee schedule validation operations.
 * Defines employee schedule validation related business operations.
 */
public interface EmployeeScheduleValidationService {

    /**
     * Maps period day configuration by weekday.
     *
     * @param days day configuration list
     * @return day configuration keyed by weekday
     */
    Map<DayOfWeek, EmployeeSchedulePeriodDayRequestDto> mapWeeklyConfigs(
            List<EmployeeSchedulePeriodDayRequestDto> days);

    /**
     * Validates a period schedule update request.
     *
     * @param request request payload
     */
    void validatePeriodRequest(EmployeeSchedulePeriodRequestDto request);

    /**
     * Validates one set of working-day hours and break settings.
     *
     * @param workingDay     working-day flag
     * @param openTime       open time
     * @param closeTime      close time
     * @param breakStartTime break start time
     * @param breakEndTime   break end time
     */
    void validateWorkingHours(
            Boolean workingDay,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime breakStartTime,
            LocalTime breakEndTime);
}
