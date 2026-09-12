package com.booking.engine.controller;

import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.service.HairSalonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST controller for hair salon information.
 * Provides read-only access to salon details.
 */
@RestController
@RequestMapping(value = "/api/v1/public/hair-salon", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class HairSalonController {

    private final HairSalonService hairSalonService;

    /**
     * Retrieves hair salon data.
     * Returns singleton salon configuration.
     *
     * @return salon details
     */
    @GetMapping
    public HairSalonResponseDto getHairSalonData() {
        return hairSalonService.getHairSalonData();
    }
}
