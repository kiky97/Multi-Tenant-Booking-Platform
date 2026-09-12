package com.booking.engine.controller;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;
import com.booking.engine.service.EmployeeScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller to manage employee schedule updates for date periods.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/admin/employees/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
public class EmployeeSchedulePeriodAdminController {

    private final EmployeeScheduleService scheduleService;

    @GetMapping("/period")
    public EmployeeSchedulePeriodResponseDto getPeriodSettings() {
        return scheduleService.getPeriodSettings();
    }

    @PutMapping(value = "/period", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmployeeSchedulePeriodResponseDto> upsertPeriod(
            @Valid @RequestBody EmployeeSchedulePeriodRequestDto request) {
        EmployeeSchedulePeriodResponseDto updated = scheduleService.upsertPeriod(request);
        return ResponseEntity.ok(updated);
    }
}
