package com.booking.engine.service;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

import com.booking.engine.dto.HairSalonHoursRequestDto;
import com.booking.engine.dto.HairSalonHoursResponseDto;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service contract for hair salon hours operations.
 * Defines hair salon hours related business operations.
 */
public interface HairSalonHoursService {

    /**
     * Retrieves full weekly schedule for a salon.
     *
     * @param hairSalonId salon identifier
     * @return list of working day DTOs
     */
    @PreAuthorize("hasRole('ADMIN')")
    List<HairSalonHoursResponseDto> getWorkingHours(UUID hairSalonId);

    /**
     * Updates schedule for one day.
     *
     * @param hairSalonId salon identifier
     * @param dayOfWeek   target day
     * @param request     update payload
     */
    @PreAuthorize("hasRole('ADMIN')")
    void updateWorkingDay(UUID hairSalonId, DayOfWeek dayOfWeek, HairSalonHoursRequestDto request);
}
