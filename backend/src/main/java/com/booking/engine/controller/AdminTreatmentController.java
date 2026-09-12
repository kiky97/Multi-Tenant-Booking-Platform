package com.booking.engine.controller;

import com.booking.engine.dto.ReorderRequestDto;
import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.service.TreatmentService;
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
 * Admin REST controller for treatment management.
 * Provides CRUD operations for services/treatments (admin only).
 */
@RestController
@RequestMapping(value = "/api/v1/admin/treatments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class AdminTreatmentController {

    private final TreatmentService treatmentService;

    /**
     * Creates a new treatment.
     *
     * @param request the treatment creation request
     * @return created treatment with generated ID
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TreatmentResponseDto> createTreatment(
            @Valid @RequestBody TreatmentRequestDto request) {

        TreatmentResponseDto created = treatmentService.createTreatment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates existing treatment.
     *
     * @param id      the treatment ID
     * @param request the update request
     * @return 204 No Content on success
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateTreatment(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody TreatmentRequestDto request) {

        treatmentService.updateTreatment(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Removes treatment by ID.
     *
     * @param id the treatment ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeTreatment(@PathVariable @NotNull UUID id) {
        treatmentService.removeTreatment(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorders two treatments by swapping their positions.
     *
     * @param request contains two treatment IDs to swap
     * @return 204 No Content on success
     */
    @PostMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorderTreatments(
            @Valid @RequestBody ReorderRequestDto request) {
        treatmentService.reorderTreatments(request.getId1(), request.getId2());
        return ResponseEntity.noContent().build();
    }
}
