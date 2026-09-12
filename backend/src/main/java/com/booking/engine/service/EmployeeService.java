package com.booking.engine.service;

import com.booking.engine.dto.EmployeeRequestDto;
import com.booking.engine.dto.EmployeeResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service contract for employee operations.
 * Defines employee related business operations.
 */
public interface EmployeeService {

    /**
     * Creates new employee.
     *
     * @param request employee data
     * @return created employee
     */
    @PreAuthorize("hasRole('ADMIN')")
    EmployeeResponseDto createEmployee(EmployeeRequestDto request);

    /**
     * Retrieves all employees sorted by display order.
     *
     * @return list of employees
     */
    List<EmployeeResponseDto> getAllEmployees();

    /**
     * Retrieves only employees that are available for booking, sorted by display
     * order.
     *
     * @return list of bookable employees
     */
    List<EmployeeResponseDto> getBookableEmployees();

    /**
     * Retrieves employee by id.
     *
     * @param id employee id
     * @return employee data
     */
    EmployeeResponseDto getEmployeeById(UUID id);

    /**
     * Updates employee data.
     *
     * @param id      employee id
     * @param request new data
     */
    @PreAuthorize("hasRole('ADMIN')")
    void updateEmployee(UUID id, EmployeeRequestDto request);

    /**
     * Deletes employee from database.
     *
     * @param id employee id
     */
    @PreAuthorize("hasRole('ADMIN')")
    void deleteEmployee(UUID id);

    /**
     * Swaps order of two employees.
     *
     * @param employeeId1 first employee
     * @param employeeId2 second employee
     */
    @PreAuthorize("hasRole('ADMIN')")
    void reorderEmployees(UUID employeeId1, UUID employeeId2);
}
