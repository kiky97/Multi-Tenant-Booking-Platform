package com.booking.engine.controller;

import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.service.TreatmentService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST controller for treatment queries.
 * Provides read-only access to service catalog.
 */
@RestController
@RequestMapping(value = "/api/v1/public/treatments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class TreatmentController {

    private final TreatmentService treatmentService;

    /**
     * Retrieves all active treatments.
     *
     * @return list of treatments
     */
    @GetMapping
    public List<TreatmentResponseDto> getAllTreatments() {
        return treatmentService.getAllTreatments();
    }

    /**
     * Retrieves treatment by ID.
     *
     * @param id the treatment ID
     * @return treatment details
     */
    @GetMapping("/{id}")
    public TreatmentResponseDto getTreatmentById(@PathVariable @NotNull UUID id) {
        return treatmentService.getTreatmentById(id);
    }
}
