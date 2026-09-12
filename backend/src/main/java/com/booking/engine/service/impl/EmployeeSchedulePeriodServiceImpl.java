package com.booking.engine.service.impl;

import com.booking.engine.dto.EmployeeSchedulePeriodDayRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;
import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.EmployeeSchedulePeriodDaySettingsEntity;
import com.booking.engine.entity.EmployeeSchedulePeriodSettingsEntity;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.EmployeeSchedulePeriodDaySettingsRepository;
import com.booking.engine.repository.EmployeeSchedulePeriodSettingsRepository;
import com.booking.engine.service.EmployeeSchedulePeriodService;
import com.booking.engine.service.EmployeeScheduleTargetResolver;
import com.booking.engine.service.EmployeeScheduleValidationService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link EmployeeSchedulePeriodService}.
 * Provides employee schedule period related business operations.
 */
@Service
@RequiredArgsConstructor
public class EmployeeSchedulePeriodServiceImpl implements EmployeeSchedulePeriodService {

    // ---------------------- Constants ----------------------

    private static final int PERIOD_SETTINGS_SINGLETON_ID = 1;

    private record ScheduleKey(UUID employeeId, LocalDate workingDate) {
    }

    // ---------------------- Repositories ----------------------

    private final EmployeeDailyScheduleRepository scheduleRepository;

    private final EmployeeSchedulePeriodSettingsRepository periodSettingsRepository;

    private final EmployeeSchedulePeriodDaySettingsRepository periodDaySettingsRepository;

    // ---------------------- Services ----------------------

    private final EmployeeScheduleValidationService employeeScheduleValidationService;

    private final EmployeeScheduleTargetResolver employeeScheduleTargetResolver;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public EmployeeSchedulePeriodResponseDto getPeriodSettings() {
        EmployeeSchedulePeriodSettingsEntity settings = getPeriodSettingsEntity();
        List<EmployeeSchedulePeriodDaySettingsEntity> daySettings = getPeriodDaySettingsEntities();
        return buildPeriodResponse(settings, daySettings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EmployeeSchedulePeriodResponseDto upsertPeriod(EmployeeSchedulePeriodRequestDto request) {
        employeeScheduleValidationService.validatePeriodRequest(request);

        Map<DayOfWeek, EmployeeSchedulePeriodDayRequestDto> scheduleByDay = employeeScheduleValidationService
                .mapWeeklyConfigs(request.getDays());
        EmployeeSchedulePeriodSettingsEntity settings = getPeriodSettingsEntity();
        List<EmployeeSchedulePeriodDaySettingsEntity> daySettings = getPeriodDaySettingsEntities();
        Map<UUID, EmployeeEntity> targetEmployees = employeeScheduleTargetResolver.resolveTargetEmployees(request,
                settings);
        List<UUID> employeeIds = new ArrayList<>(targetEmployees.keySet());

        settings.setApplyToAllEmployees(request.getApplyToAllEmployees());
        settings.setStartDate(request.getStartDate());
        settings.setEndDate(request.getEndDate());
        updatePeriodDaySettings(daySettings, scheduleByDay);

        periodSettingsRepository.save(settings);
        periodDaySettingsRepository.saveAll(daySettings);

        Map<ScheduleKey, EmployeeDailyScheduleEntity> existing = scheduleRepository
                .findByEmployeeIdInAndWorkingDateBetween(employeeIds, request.getStartDate(), request.getEndDate())
                .stream()
                .collect(Collectors.toMap(
                        entity -> new ScheduleKey(entity.getEmployee().getId(), entity.getWorkingDate()),
                        entity -> entity,
                        (first, second) -> first,
                        HashMap::new));

        List<EmployeeDailyScheduleEntity> entitiesToSave = new ArrayList<>();

        request.getStartDate().datesUntil(request.getEndDate().plusDays(1)).forEach(date -> {
            EmployeeSchedulePeriodDayRequestDto dayConfig = scheduleByDay.get(date.getDayOfWeek());

            targetEmployees.values().forEach(employee -> {
                ScheduleKey key = new ScheduleKey(employee.getId(), date);
                EmployeeDailyScheduleEntity entity = existing.get(key);

                if (entity == null) {
                    entity = new EmployeeDailyScheduleEntity();
                    entity.setEmployee(employee);
                    entity.setWorkingDate(date);
                }

                applyDayConfig(entity, dayConfig);
                entitiesToSave.add(entity);
            });
        });

        scheduleRepository.saveAll(entitiesToSave);
        return buildPeriodResponse(settings, daySettings);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Applies one weekday configuration to the target daily schedule entity.
     */
    private void applyDayConfig(EmployeeDailyScheduleEntity entity, EmployeeSchedulePeriodDayRequestDto config) {
        entity.setWorkingDay(Boolean.TRUE.equals(config.getWorkingDay()));
        entity.setOpenTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getOpenTime() : null);
        entity.setCloseTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getCloseTime() : null);
        entity.setBreakStartTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getBreakStartTime() : null);
        entity.setBreakEndTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getBreakEndTime() : null);
    }

    /*
     * Updates stored weekday period settings using the provided request data.
     */
    private void updatePeriodDaySettings(
            List<EmployeeSchedulePeriodDaySettingsEntity> daySettings,
            Map<DayOfWeek, EmployeeSchedulePeriodDayRequestDto> scheduleByDay) {
        daySettings.forEach(entity -> {
            EmployeeSchedulePeriodDayRequestDto config = scheduleByDay.get(entity.getDayOfWeek());
            entity.setWorkingDay(config.getWorkingDay());
            entity.setOpenTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getOpenTime() : null);
            entity.setCloseTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getCloseTime() : null);
            entity.setBreakStartTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getBreakStartTime() : null);
            entity.setBreakEndTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getBreakEndTime() : null);
        });
    }

    /*
     * Builds the response DTO for employee schedule period settings.
     */
    private EmployeeSchedulePeriodResponseDto buildPeriodResponse(
            EmployeeSchedulePeriodSettingsEntity settings,
            List<EmployeeSchedulePeriodDaySettingsEntity> daySettings) {
        return EmployeeSchedulePeriodResponseDto.builder()
                .startDate(settings.getStartDate())
                .endDate(settings.getEndDate())
                .employeeId(settings.getTargetEmployee() != null ? settings.getTargetEmployee().getId() : null)
                .applyToAllEmployees(settings.getApplyToAllEmployees())
                .days(daySettings.stream()
                        .sorted(Comparator.comparingInt(item -> item.getDayOfWeek().getValue()))
                        .map(item -> EmployeeSchedulePeriodDayRequestDto.builder()
                                .dayOfWeek(item.getDayOfWeek())
                                .workingDay(item.getWorkingDay())
                                .openTime(item.getOpenTime())
                                .closeTime(item.getCloseTime())
                                .breakStartTime(item.getBreakStartTime())
                                .breakEndTime(item.getBreakEndTime())
                                .build())
                        .toList())
                .build();
    }

    /*
     * Retrieves singleton employee schedule period settings.
     */
    private EmployeeSchedulePeriodSettingsEntity getPeriodSettingsEntity() {
        return periodSettingsRepository.findById(PERIOD_SETTINGS_SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Employee schedule period settings not initialized. Run DB migrations."));
    }

    /*
     * Retrieves and validates weekday settings for the period configuration.
     */
    private List<EmployeeSchedulePeriodDaySettingsEntity> getPeriodDaySettingsEntities() {
        List<EmployeeSchedulePeriodDaySettingsEntity> daySettings = new ArrayList<>(
                periodDaySettingsRepository.findAll());
        if (daySettings.size() != DayOfWeek.values().length) {
            throw new IllegalStateException(
                    "Employee schedule period weekday settings are not initialized. Run DB migrations.");
        }
        daySettings.sort(Comparator.comparingInt(item -> item.getDayOfWeek().getValue()));
        return daySettings;
    }
}
