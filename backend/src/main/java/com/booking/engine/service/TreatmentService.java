package com.booking.engine.service;

import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service contract for treatment operations.
 * Defines treatment related business operations.
 */
public interface TreatmentService {

    /**
     * Creates a new treatment.
     *
     * @param request treatment request payload
     * @return created treatment DTO
     */
    @PreAuthorize("hasRole('ADMIN')")
    TreatmentResponseDto createTreatment(TreatmentRequestDto request);

    /**
     * Retrieves all active treatments sorted by display order.
     *
     * @return list of treatment DTOs
     */
    List<TreatmentResponseDto> getAllTreatments();

    /**
     * Updates treatment data.
     *
     * @param id      treatment identifier
     * @param request update payload
     */
    @PreAuthorize("hasRole('ADMIN')")
    void updateTreatment(UUID id, TreatmentRequestDto request);

    /**
     * Removes treatment by identifier.
     *
     * @param id treatment identifier
     */
    @PreAuthorize("hasRole('ADMIN')")
    void removeTreatment(UUID id);

    /**
     * Retrieves treatment by identifier.
     *
     * @param id treatment identifier
     * @return treatment DTO
     */
    TreatmentResponseDto getTreatmentById(UUID id);

    /**
     * Swaps order of two treatments.
     *
     * @param treatmentId1 first treatment
     * @param treatmentId2 second treatment
     */
    @PreAuthorize("hasRole('ADMIN')")
    void reorderTreatments(UUID treatmentId1, UUID treatmentId2);
}
