package com.booking.engine.controller;

import com.booking.engine.dto.EmployeeRequestDto;
import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.dto.ReorderRequestDto;
import com.booking.engine.service.EmployeeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for employee management.
 * Provides CRUD operations for employees (admin only).
 */
@RestController
@RequestMapping(value = "/api/v1/admin/employees", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class AdminEmployeeController {

    private final EmployeeService employeeService;

    /**
     * Creates a new employee.
     *
     * @param request the employee creation request
     * @return created employee with generated ID
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmployeeResponseDto> createEmployee(
            @Valid @RequestBody EmployeeRequestDto request) {

        EmployeeResponseDto created = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates existing employee.
     *
     * @param id      the employee ID
     * @param request the update request
     * @return 204 No Content on success
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateEmployee(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody EmployeeRequestDto request) {

        employeeService.updateEmployee(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes employee by ID.
     *
     * @param id the employee ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable @NotNull UUID id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorders two employees by swapping their positions.
     *
     * @param request contains two employee IDs to swap
     * @return 204 No Content on success
     */
    @PostMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorderEmployees(
            @Valid @RequestBody ReorderRequestDto request) {
        employeeService.reorderEmployees(request.getId1(), request.getId2());
        return ResponseEntity.noContent().build();
    }
}
