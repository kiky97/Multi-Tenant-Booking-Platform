package com.booking.engine.service.impl;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.EmployeeSchedulePeriodSettingsEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.service.EmployeeScheduleTargetResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link EmployeeScheduleTargetResolver}.
 * Provides employee schedule target resolver related business operations.
 */
@Service
@RequiredArgsConstructor
public class EmployeeScheduleTargetResolverImpl implements EmployeeScheduleTargetResolver {
    // ---------------------- Repositories ----------------------

    private final EmployeeRepository employeeRepository;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public EmployeeEntity resolveEmployeeOrThrow(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<UUID, EmployeeEntity> resolveTargetEmployees(
            EmployeeSchedulePeriodRequestDto request,
            EmployeeSchedulePeriodSettingsEntity settings) {
        if (Boolean.TRUE.equals(request.getApplyToAllEmployees())) {
            settings.setTargetEmployee(null);
            List<EmployeeEntity> employees = employeeRepository
                    .findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc();
            if (employees.isEmpty()) {
                throw new IllegalArgumentException("No active employees found for period update.");
            }

            LinkedHashMap<UUID, EmployeeEntity> targetEmployees = new LinkedHashMap<>();
            employees.forEach(employee -> targetEmployees.put(employee.getId(), employee));
            return targetEmployees;
        }

        EmployeeEntity employee = resolveEmployeeOrThrow(request.getEmployeeId());
        settings.setTargetEmployee(employee);

        LinkedHashMap<UUID, EmployeeEntity> targetEmployees = new LinkedHashMap<>();
        targetEmployees.put(employee.getId(), employee);
        return targetEmployees;
    }
}
