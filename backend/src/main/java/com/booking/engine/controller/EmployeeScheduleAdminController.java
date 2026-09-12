package com.booking.engine.controller;

import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import com.booking.engine.service.EmployeeScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller to manage per-date employee schedule.
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping(value = "/api/v1/admin/employees/{employeeId}/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
public class EmployeeScheduleAdminController {

    private final EmployeeScheduleService scheduleService;

    @GetMapping
    public List<EmployeeScheduleResponseDto> getSchedule(
            @PathVariable @NotNull UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleService.getSchedule(employeeId, from, to);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> upsertDay(
            @PathVariable @NotNull UUID employeeId,
            @Valid @RequestBody EmployeeScheduleRequestDto request) {
        scheduleService.upsertDay(employeeId, request);
        return ResponseEntity.noContent().build();
    }
}
