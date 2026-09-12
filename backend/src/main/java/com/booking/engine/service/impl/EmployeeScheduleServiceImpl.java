package com.booking.engine.service.impl;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;
import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import com.booking.engine.service.EmployeeScheduleDayService;
import com.booking.engine.service.EmployeeSchedulePeriodService;
import com.booking.engine.service.EmployeeScheduleService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link EmployeeScheduleService}.
 * Provides employee schedule related business operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeScheduleServiceImpl implements EmployeeScheduleService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(EmployeeScheduleServiceImpl.class);

    // ---------------------- Services ----------------------

    private final EmployeeScheduleDayService employeeScheduleDayService;

    private final EmployeeSchedulePeriodService employeeSchedulePeriodService;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EmployeeScheduleResponseDto> getSchedule(UUID employeeId, LocalDate from, LocalDate to) {
        return employeeScheduleDayService.getSchedule(employeeId, from, to);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void upsertDay(UUID employeeId, EmployeeScheduleRequestDto request) {
        employeeScheduleDayService.upsertDay(employeeId, request);
        log.info("event=employee_schedule_day_upserted employeeId={} workingDate={} workingDay={}",
                employeeId,
                request.getWorkingDate(),
                request.getWorkingDay());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EmployeeSchedulePeriodResponseDto getPeriodSettings() {
        return employeeSchedulePeriodService.getPeriodSettings();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EmployeeSchedulePeriodResponseDto upsertPeriod(EmployeeSchedulePeriodRequestDto request) {
        EmployeeSchedulePeriodResponseDto response = employeeSchedulePeriodService.upsertPeriod(request);
        log.info(
                "event=employee_schedule_period_upserted startDate={} endDate={} employeeId={} allEmployees={} dayCount={}",
                response.getStartDate(),
                response.getEndDate(),
                response.getEmployeeId(),
                response.getApplyToAllEmployees(),
                response.getDays() != null ? response.getDays().size() : 0);
        return response;
    }
}
