package com.booking.engine.controller;

import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.service.HairSalonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for hair salon configuration.
 * Manages salon settings (singleton resource).
 */
@RestController
@RequestMapping(value = "/api/v1/admin/hair-salon", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AdminHairSalonController {

    private final HairSalonService hairSalonService;

    /**
     * Updates hair salon data.
     * Since it's a singleton, no ID in path.
     *
     * @param request the update request
     * @return 204 No Content on success
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateHairSalonData(
            @Valid @RequestBody HairSalonRequestDto request) {

        hairSalonService.updateHairSalonData(request);
        return ResponseEntity.noContent().build();
    }
}
