package com.booking.engine.controller;

import com.booking.engine.dto.HairSalonHoursRequestDto;
import com.booking.engine.dto.HairSalonHoursResponseDto;
import com.booking.engine.service.HairSalonHoursService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for hair salon working hours management.
 * Manages weekly schedule configuration.
 */
@RestController
@RequestMapping(value = "/api/v1/admin/hair-salons/{hairSalonId}/hours", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class HairSalonHoursAdminController {

    private final HairSalonHoursService hoursService;

    /**
     * Retrieves working hours for a hair salon.
     *
     * @param hairSalonId the salon ID
     * @return list of working hours for each day
     */
    @GetMapping
    public List<HairSalonHoursResponseDto> getAllHours(@PathVariable @NotNull UUID hairSalonId) {
        return hoursService.getWorkingHours(hairSalonId);
    }

    /**
     * Updates working hours for specific day of week.
     *
     * @param hairSalonId the salon ID
     * @param dayOfWeek   the day to update (MONDAY, TUESDAY, etc.)
     * @param request     the hours update request
     * @return 204 No Content on success
     */
    @PutMapping(value = "/{dayOfWeek}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateHours(
            @PathVariable @NotNull UUID hairSalonId,
            @PathVariable @NotNull DayOfWeek dayOfWeek,
            @Valid @RequestBody HairSalonHoursRequestDto request) {

        hoursService.updateWorkingDay(hairSalonId, dayOfWeek, request);
        return ResponseEntity.noContent().build();
    }
}
