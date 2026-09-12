package com.booking.engine.repository;

import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for per-date employee schedules. */
@Repository
public interface EmployeeDailyScheduleRepository extends JpaRepository<EmployeeDailyScheduleEntity, UUID> {

    Optional<EmployeeDailyScheduleEntity> findByEmployeeIdAndWorkingDate(UUID employeeId, LocalDate workingDate);

    List<EmployeeDailyScheduleEntity> findByEmployeeIdAndWorkingDateBetweenOrderByWorkingDateAsc(
            UUID employeeId, LocalDate start, LocalDate end);

    List<EmployeeDailyScheduleEntity> findByEmployeeIdInAndWorkingDateBetween(
            Collection<UUID> employeeIds, LocalDate start, LocalDate end);

    long deleteByWorkingDateBefore(LocalDate workingDate);
}
