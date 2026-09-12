package com.booking.engine.service;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.EmployeeSchedulePeriodSettingsEntity;
import java.util.Map;
import java.util.UUID;

/**
 * Service contract for employee schedule target resolver operations.
 * Defines employee schedule target resolver related business operations.
 */
public interface EmployeeScheduleTargetResolver {

    /**
     * Resolves a single active employee or fails when it does not exist.
     *
     * @param employeeId employee identifier
     * @return employee entity
     */
    EmployeeEntity resolveEmployeeOrThrow(UUID employeeId);

    /**
     * Resolves all employees targeted by a period schedule request.
     *
     * @param request  request payload
     * @param settings settings entity
     * @return employee entities keyed by identifier
     */
    Map<UUID, EmployeeEntity> resolveTargetEmployees(
            EmployeeSchedulePeriodRequestDto request,
            EmployeeSchedulePeriodSettingsEntity settings);
}
