package com.booking.engine.service.impl;

import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.TreatmentMapper;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.DisplayOrderService;
import com.booking.engine.service.TreatmentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TreatmentService}.
 * Provides treatment related business operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TreatmentServiceImpl implements TreatmentService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(TreatmentServiceImpl.class);

    // ---------------------- Repositories ----------------------

    private final TreatmentRepository treatmentRepository;

    // ---------------------- Mappers ----------------------

    private final TreatmentMapper mapper;

    // ---------------------- Services ----------------------

    private final DisplayOrderService displayOrderService;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TreatmentResponseDto createTreatment(TreatmentRequestDto request) {

        // Lock active treatment ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(treatmentRepository);

        Integer order = displayOrderService.resolveDisplayOrder(
                request.getDisplayOrder(), treatmentRepository);

        displayOrderService.shiftDisplayOrders(order, treatmentRepository);

        TreatmentEntity treatment = mapper.toEntity(request);
        treatment.setDisplayOrder(order);

        TreatmentEntity savedTreatment = treatmentRepository.save(treatment);

        log.info("event=treatment_created treatmentId={} displayOrder={}",
                savedTreatment.getId(), savedTreatment.getDisplayOrder());

        return mapper.toDto(savedTreatment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TreatmentResponseDto> getAllTreatments() {
        return treatmentRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TreatmentResponseDto getTreatmentById(UUID id) {
        TreatmentEntity treatment = findTreatmentOrThrow(id);

        return mapper.toDto(treatment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateTreatment(UUID id, TreatmentRequestDto request) {

        // Lock active treatment ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(treatmentRepository);

        TreatmentEntity treatment = findTreatmentOrThrow(id);

        Integer oldOrder = treatment.getDisplayOrder();
        Integer newOrder = displayOrderService.resolveDisplayOrderForUpdate(
                request.getDisplayOrder(), oldOrder, treatmentRepository);

        if (!oldOrder.equals(newOrder)) {
            displayOrderService.moveDisplayOrder(oldOrder, newOrder, treatmentRepository);
        }

        mapper.updateFromDto(request, treatment);
        treatment.setDisplayOrder(newOrder);

        log.info("event=treatment_updated treatmentId={} oldDisplayOrder={} newDisplayOrder={}",
                id, oldOrder, newOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void removeTreatment(UUID id) {

        // Lock active treatment ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(treatmentRepository);

        TreatmentEntity treatment = findTreatmentOrThrow(id);
        Integer removedOrder = treatment.getDisplayOrder();

        treatmentRepository.delete(treatment);

        displayOrderService.shiftOrdersAfterDelete(removedOrder, treatmentRepository);

        log.info("event=treatment_deleted treatmentId={} freedDisplayOrder={}",
                id, removedOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void reorderTreatments(UUID treatmentId1, UUID treatmentId2) {

        // Lock active treatment ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(treatmentRepository);

        if (treatmentId1.equals(treatmentId2)) {
            throw new IllegalArgumentException("Cannot reorder the same treatment id");
        }

        TreatmentEntity treatment1 = findTreatmentOrThrow(treatmentId1);
        TreatmentEntity treatment2 = findTreatmentOrThrow(treatmentId2);

        Integer order1 = treatment1.getDisplayOrder();
        Integer order2 = treatment2.getDisplayOrder();

        Integer tempOrder = displayOrderService.resolveTemporaryDisplayOrder(treatmentRepository);

        treatment1.setDisplayOrder(tempOrder);
        treatmentRepository.saveAndFlush(treatment1);

        treatment2.setDisplayOrder(order1);
        treatmentRepository.saveAndFlush(treatment2);

        treatment1.setDisplayOrder(order2);
        treatmentRepository.saveAndFlush(treatment1);

        log.info(
                "event=treatment_reordered treatmentId1={} newDisplayOrder1={} treatmentId2={} newDisplayOrder2={}",
                treatmentId1, order2, treatmentId2, order1);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Centralizes active treatment lookup and not-found logging for treatment
     * operations.
     */
    private TreatmentEntity findTreatmentOrThrow(UUID id) {
        return treatmentRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> {
                    log.warn("event=treatment_lookup_failed reason=not_found treatmentId={}", id);
                    return new EntityNotFoundException("Treatment", id);
                });
    }
}
