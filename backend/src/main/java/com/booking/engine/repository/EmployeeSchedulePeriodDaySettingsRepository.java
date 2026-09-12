package com.booking.engine.repository;

import com.booking.engine.entity.EmployeeSchedulePeriodDaySettingsEntity;
import java.time.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for persisted weekly rows of the admin per-period employee
 * schedule form.
 */
@Repository
public interface EmployeeSchedulePeriodDaySettingsRepository
        extends JpaRepository<EmployeeSchedulePeriodDaySettingsEntity, DayOfWeek> {
}
