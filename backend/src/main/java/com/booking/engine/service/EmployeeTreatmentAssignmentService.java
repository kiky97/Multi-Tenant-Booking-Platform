package com.booking.engine.service;

import com.booking.engine.entity.TreatmentEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service contract for employee treatment assignment operations.
 * Defines employee treatment assignment related business operations.
 */
public interface EmployeeTreatmentAssignmentService {

    /**
     * Resolves active treatment entities for an employee assignment request.
     *
     * @param treatmentIds requested treatment identifiers
     * @return active treatment entities
     */
    Set<TreatmentEntity> resolveRequestedTreatments(List<UUID> treatmentIds);
}
