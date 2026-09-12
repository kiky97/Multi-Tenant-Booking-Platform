package com.booking.engine.service.impl;

import com.booking.engine.dto.EmployeeSchedulePeriodDayRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.service.EmployeeScheduleValidationService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link EmployeeScheduleValidationService}.
 * Provides employee schedule validation related business operations.
 */
@Service
public class EmployeeScheduleValidationServiceImpl implements EmployeeScheduleValidationService {
    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<DayOfWeek, EmployeeSchedulePeriodDayRequestDto> mapWeeklyConfigs(
            List<EmployeeSchedulePeriodDayRequestDto> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("At least one weekday configuration is required.");
        }

        Map<DayOfWeek, EmployeeSchedulePeriodDayRequestDto> result = new EnumMap<>(DayOfWeek.class);
        for (EmployeeSchedulePeriodDayRequestDto day : days) {
            if (day.getDayOfWeek() == null) {
                throw new IllegalArgumentException("Day of week is required for every period row.");
            }
            if (result.put(day.getDayOfWeek(), day) != null) {
                throw new IllegalArgumentException("Duplicate weekday configuration: " + day.getDayOfWeek());
            }

            validateWorkingHours(
                    day.getWorkingDay(),
                    day.getOpenTime(),
                    day.getCloseTime(),
                    day.getBreakStartTime(),
                    day.getBreakEndTime());
        }

        if (result.size() != DayOfWeek.values().length) {
            throw new IllegalArgumentException("All seven weekday configurations must be provided.");
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePeriodRequest(EmployeeSchedulePeriodRequestDto request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required for period update.");
        }

        if (request.getApplyToAllEmployees() == null) {
            throw new IllegalArgumentException("Apply-to-all-employees flag is required for period update.");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date must be earlier than or equal to end date.");
        }

        if (Boolean.TRUE.equals(request.getApplyToAllEmployees()) && request.getEmployeeId() != null) {
            throw new IllegalArgumentException("Choose either all employees or one employee for period update.");
        }

        if (!Boolean.TRUE.equals(request.getApplyToAllEmployees()) && request.getEmployeeId() == null) {
            throw new IllegalArgumentException("Employee id is required when period update is not for all employees.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateWorkingHours(
            Boolean workingDay,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime breakStartTime,
            LocalTime breakEndTime) {
        if (!Boolean.TRUE.equals(workingDay)) {
            return;
        }

        if (openTime == null || closeTime == null) {
            throw new IllegalArgumentException("Open and close times are required for a working day.");
        }

        if (!openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("Open time must be earlier than close time.");
        }

        if (breakStartTime == null && breakEndTime == null) {
            return;
        }

        if (breakStartTime == null || breakEndTime == null) {
            throw new IllegalArgumentException(
                    "Break start and end times must both be provided, or both omitted.");
        }

        if (!breakStartTime.isBefore(breakEndTime)) {
            throw new IllegalArgumentException("Break start time must be earlier than break end time.");
        }

        if (Duration.between(breakStartTime, breakEndTime).toMinutes() != 60) {
            throw new IllegalArgumentException("Break time must be exactly 60 minutes.");
        }

        if (breakStartTime.isBefore(openTime) || breakEndTime.isAfter(closeTime)) {
            throw new IllegalArgumentException("Break time must be inside the employee working hours.");
        }
    }
}
