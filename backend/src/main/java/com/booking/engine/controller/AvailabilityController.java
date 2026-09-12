package com.booking.engine.controller;

import com.booking.engine.dto.AvailabilitySlotDto;
import com.booking.engine.service.AvailabilityService;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public controller exposing read-only availability information.
 */
@RestController
@RequestMapping(value = "/api/v1/public/availability", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    public List<AvailabilitySlotDto> getAvailability(
            @RequestParam @NotNull UUID employeeId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @NotNull UUID treatmentId) {
        return availabilityService.getAvailability(employeeId, date, treatmentId);
    }
}
