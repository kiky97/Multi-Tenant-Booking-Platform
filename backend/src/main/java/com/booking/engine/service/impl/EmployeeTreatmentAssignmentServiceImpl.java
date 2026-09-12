package com.booking.engine.service.impl;

import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.EmployeeTreatmentAssignmentService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link EmployeeTreatmentAssignmentService}.
 * Provides employee treatment assignment related business operations.
 */
@Service
@RequiredArgsConstructor
public class EmployeeTreatmentAssignmentServiceImpl implements EmployeeTreatmentAssignmentService {
    // ---------------------- Repositories ----------------------

    private final TreatmentRepository treatmentRepository;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<TreatmentEntity> resolveRequestedTreatments(List<UUID> treatmentIds) {
        LinkedHashSet<UUID> requestedIds = normalizeTreatmentIds(treatmentIds);
        if (requestedIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        List<TreatmentEntity> activeTreatments = treatmentRepository.findAllByIdInAndActiveTrue(requestedIds);
        if (activeTreatments.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Some selected services no longer exist or are inactive.");
        }

        return new LinkedHashSet<>(activeTreatments);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Normalizes requested treatment identifiers by removing null values while
     * preserving their original order.
     */
    private LinkedHashSet<UUID> normalizeTreatmentIds(List<UUID> treatmentIds) {
        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();
        if (treatmentIds == null) {
            return normalized;
        }

        treatmentIds.stream()
                .filter(id -> id != null)
                .forEach(normalized::add);

        return normalized;
    }
}
