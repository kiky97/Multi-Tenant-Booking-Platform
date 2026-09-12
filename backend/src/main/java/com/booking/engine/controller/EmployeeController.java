package com.booking.engine.controller;

import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.service.EmployeeService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST controller for employee queries.
 * Provides read-only access to employee information.
 */
@RestController
@RequestMapping(value = "/api/v1/public/employees", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * Retrieves all active employees sorted by display order.
     *
     * @return list of employees in display order
     */
    @GetMapping
    public List<EmployeeResponseDto> getAllEmployees(
            @RequestParam(name = "bookable", defaultValue = "false") boolean bookable) {
        return bookable
                ? employeeService.getBookableEmployees()
                : employeeService.getAllEmployees();
    }

    /**
     * Retrieves employee by ID.
     *
     * @param id the employee ID
     * @return employee details
     */
    @GetMapping("/{id}")
    public EmployeeResponseDto getEmployeeById(@PathVariable @NotNull UUID id) {
        return employeeService.getEmployeeById(id);
    }
}
