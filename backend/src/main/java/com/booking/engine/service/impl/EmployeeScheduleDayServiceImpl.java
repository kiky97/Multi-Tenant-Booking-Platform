package com.booking.engine.service.impl;

import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.mapper.EmployeeScheduleMapper;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.service.EmployeeScheduleDayService;
import com.booking.engine.service.EmployeeScheduleTargetResolver;
import com.booking.engine.service.EmployeeScheduleValidationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link EmployeeScheduleDayService}.
 * Provides employee schedule day related business operations.
 */
@Service
@RequiredArgsConstructor
public class EmployeeScheduleDayServiceImpl implements EmployeeScheduleDayService {
    // ---------------------- Repositories ----------------------

    private final EmployeeDailyScheduleRepository scheduleRepository;

    // ---------------------- Mappers ----------------------

    private final EmployeeScheduleMapper mapper;

    // ---------------------- Services ----------------------

    private final EmployeeScheduleValidationService employeeScheduleValidationService;

    private final EmployeeScheduleTargetResolver employeeScheduleTargetResolver;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EmployeeScheduleResponseDto> getSchedule(UUID employeeId, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusMonths(1);

        Map<LocalDate, EmployeeDailyScheduleEntity> existing = scheduleRepository
                .findByEmployeeIdAndWorkingDateBetweenOrderByWorkingDateAsc(employeeId, start, end)
                .stream()
                .collect(Collectors.toMap(EmployeeDailyScheduleEntity::getWorkingDate, entity -> entity, (a, b) -> a));

        return start.datesUntil(end.plusDays(1))
                .map(date -> {
                    EmployeeDailyScheduleEntity entity = existing.get(date);
                    if (entity != null) {
                        return mapper.toDto(entity);
                    }
                    return EmployeeScheduleResponseDto.builder()
                            .id(null)
                            .workingDate(date)
                            .workingDay(false)
                            .openTime(null)
                            .closeTime(null)
                            .breakStartTime(null)
                            .breakEndTime(null)
                            .build();
                })
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void upsertDay(UUID employeeId, EmployeeScheduleRequestDto request) {
        employeeScheduleValidationService.validateWorkingHours(
                request.getWorkingDay(),
                request.getOpenTime(),
                request.getCloseTime(),
                request.getBreakStartTime(),
                request.getBreakEndTime());

        EmployeeEntity employee = employeeScheduleTargetResolver.resolveEmployeeOrThrow(employeeId);

        EmployeeDailyScheduleEntity entity = scheduleRepository
                .findByEmployeeIdAndWorkingDate(employeeId, request.getWorkingDate())
                .orElseGet(() -> {
                    EmployeeDailyScheduleEntity newEntity = mapper.toEntity(request);
                    newEntity.setEmployee(employee);
                    return newEntity;
                });

        applyDailySchedule(
                entity,
                request.getWorkingDay(),
                request.getOpenTime(),
                request.getCloseTime(),
                request.getBreakStartTime(),
                request.getBreakEndTime());

        scheduleRepository.save(entity);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Applies daily schedule values to the target employee schedule entity.
     */
    private void applyDailySchedule(
            EmployeeDailyScheduleEntity entity,
            Boolean workingDay,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime breakStartTime,
            LocalTime breakEndTime) {
        entity.setWorkingDay(Boolean.TRUE.equals(workingDay));
        entity.setOpenTime(Boolean.TRUE.equals(workingDay) ? openTime : null);
        entity.setCloseTime(Boolean.TRUE.equals(workingDay) ? closeTime : null);
        entity.setBreakStartTime(Boolean.TRUE.equals(workingDay) ? breakStartTime : null);
        entity.setBreakEndTime(Boolean.TRUE.equals(workingDay) ? breakEndTime : null);
    }
}
