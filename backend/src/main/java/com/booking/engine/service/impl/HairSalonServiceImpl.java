package com.booking.engine.service.impl;

import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.HairSalonMapper;
import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.repository.HairSalonRepository;
import com.booking.engine.service.HairSalonService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link HairSalonService}.
 * Provides hair salon related business operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HairSalonServiceImpl implements HairSalonService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(HairSalonServiceImpl.class);

    // ---------------------- Repositories ----------------------

    private final HairSalonRepository hairSalonRepository;

    // ---------------------- Mappers ----------------------

    private final HairSalonMapper mapper;

    // ---------------------- Properties ----------------------

    /** Default hair salon ID configuration. */
    private final HairSalonProperties properties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public HairSalonResponseDto getHairSalonData() {
        HairSalonEntity salon = findSalonOrThrow(properties.getId());
        return mapper.toDto(salon);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateHairSalonData(HairSalonRequestDto request) {
        HairSalonEntity salon = findSalonOrThrow(properties.getId());
        mapper.updateFromDto(request, salon);

        log.info("event=hair_salon_updated hairSalonId={}", properties.getId());
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Loads singleton salon configuration and keeps not-found handling
     * consistent for admin and public callers.
     */
    private HairSalonEntity findSalonOrThrow(java.util.UUID id) {
        return hairSalonRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("event=hair_salon_lookup_failed reason=not_found hairSalonId={}", id);
                    return new EntityNotFoundException("HairSalon", id);
                });
    }
}
