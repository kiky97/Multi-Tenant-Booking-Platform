package com.booking.engine.repository;

import com.booking.engine.entity.HairSalonHoursEntity;
import java.time.DayOfWeek;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for managing {@link HairSalonHoursEntity} persistence. */
@Repository
public interface HairSalonHoursRepository
        extends JpaRepository<HairSalonHoursEntity, UUID> {

    Optional<HairSalonHoursEntity> findByHairSalonIdAndDayOfWeek(
            UUID hairSalonId,
            DayOfWeek dayOfWeek);
}
