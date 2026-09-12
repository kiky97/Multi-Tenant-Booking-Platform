package com.booking.engine.repository;

import com.booking.engine.entity.EmployeeSchedulePeriodSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for the singleton per-period employee schedule form state. */
@Repository
public interface EmployeeSchedulePeriodSettingsRepository
        extends JpaRepository<EmployeeSchedulePeriodSettingsEntity, Integer> {
}
