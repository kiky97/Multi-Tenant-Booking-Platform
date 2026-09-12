package com.booking.engine.service.impl;

import com.booking.engine.dto.HairSalonHoursRequestDto;
import com.booking.engine.dto.HairSalonHoursResponseDto;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.entity.HairSalonHoursEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.HairSalonHoursMapper;
import com.booking.engine.repository.HairSalonHoursRepository;
import com.booking.engine.repository.HairSalonRepository;
import com.booking.engine.service.HairSalonHoursService;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link HairSalonHoursService}.
 * Provides hair salon hours related business operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HairSalonHoursServiceImpl implements HairSalonHoursService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(HairSalonHoursServiceImpl.class);

    // ---------------------- Repositories ----------------------

    private final HairSalonRepository hairSalonRepository;

    private final HairSalonHoursRepository hoursRepository;

    // ---------------------- Mappers ----------------------

    private final HairSalonHoursMapper mapper;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public List<HairSalonHoursResponseDto> getWorkingHours(UUID hairSalonId) {
        HairSalonEntity salon = hairSalonRepository.findById(hairSalonId)
                .orElseThrow(() -> {
                    log.warn("event=hair_salon_lookup_failed reason=not_found hairSalonId={}", hairSalonId);
                    return new EntityNotFoundException("HairSalon", hairSalonId);
                });

        return salon.getWorkingHours()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateWorkingDay(UUID hairSalonId, DayOfWeek dayOfWeek, HairSalonHoursRequestDto request) {
        HairSalonHoursEntity hours = hoursRepository
                .findByHairSalonIdAndDayOfWeek(hairSalonId, dayOfWeek)
                .orElseThrow(() -> {
                    log.warn(
                            "event=hair_salon_hours_lookup_failed reason=not_found hairSalonId={} dayOfWeek={}",
                            hairSalonId,
                            dayOfWeek);
                    return new EntityNotFoundException(
                            "Working hours for salon " + hairSalonId + " on " + dayOfWeek);
                });

        if (!request.getWorkingDay()) {
            setNonWorkingDay(hours);
            log.info("event=hair_salon_hours_marked_non_working hairSalonId={} dayOfWeek={}",
                    hairSalonId,
                    dayOfWeek);
            return;
        }

        setWorkingDay(hours, request);
        log.info("event=hair_salon_hours_updated hairSalonId={} dayOfWeek={} openTime={} closeTime={}",
                hairSalonId, dayOfWeek, request.getOpenTime(), request.getCloseTime());
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Sets the day as non-working.
     */
    private void setNonWorkingDay(HairSalonHoursEntity hours) {
        hours.setWorkingDay(false);
        hours.setOpenTime(null);
        hours.setCloseTime(null);
    }

    /*
     * Sets the day as working with validated times.
     */
    private void setWorkingDay(HairSalonHoursEntity hours, HairSalonHoursRequestDto request) {
        validateWorkingDayTimes(request);

        hours.setWorkingDay(true);
        hours.setOpenTime(request.getOpenTime());
        hours.setCloseTime(request.getCloseTime());
    }

    /*
     * Validates working day times.
     */
    private void validateWorkingDayTimes(HairSalonHoursRequestDto request) {
        if (request.getOpenTime() == null || request.getCloseTime() == null) {
            throw new IllegalArgumentException("Open and close time must be provided for working day");
        }

        if (!request.getCloseTime().isAfter(request.getOpenTime())) {
            throw new IllegalArgumentException("Close time must be after open time");
        }
    }
}
