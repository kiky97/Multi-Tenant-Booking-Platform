package com.booking.engine.service;

import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.dto.HairSalonResponseDto;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service contract for hair salon operations.
 * Defines hair salon related business operations.
 */
public interface HairSalonService {

    /**
     * Retrieves singleton hair salon data.
     *
     * @return hair salon DTO
     */
    HairSalonResponseDto getHairSalonData();

    /**
     * Updates singleton hair salon data.
     *
     * @param request update payload
     */
    @PreAuthorize("hasRole('ADMIN')")
    void updateHairSalonData(HairSalonRequestDto request);
}
