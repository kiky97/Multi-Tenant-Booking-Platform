package com.booking.engine.repository;

import com.booking.engine.entity.EmployeeEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing {@link EmployeeEntity}.
 * Provides database operations for employee persistence
 * and ordering logic.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<EmployeeEntity, UUID>,
        DisplayOrderRepository<EmployeeEntity> {

    List<EmployeeEntity> findAllByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Retrieves all active employees with treatments sorted by display order.
     *
     * @return ordered list of active employees with treatments
     */
    @Query("""
            SELECT DISTINCT e
            FROM EmployeeEntity e
            LEFT JOIN FETCH e.providedTreatments
            WHERE e.active = true
            ORDER BY e.displayOrder ASC
            """)
    List<EmployeeEntity> findAllActiveWithTreatmentsOrderByDisplayOrderAsc();

    List<EmployeeEntity> findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc();

    /**
     * Retrieves all active and bookable employees with treatments sorted by display
     * order.
     *
     * @return ordered list of active bookable employees with treatments
     */
    @Query("""
            SELECT DISTINCT e
            FROM EmployeeEntity e
            LEFT JOIN FETCH e.providedTreatments
            WHERE e.active = true AND e.bookable = true
            ORDER BY e.displayOrder ASC
            """)
    List<EmployeeEntity> findAllBookableWithTreatmentsOrderByDisplayOrderAsc();

    @Override
    @Query("SELECT MAX(b.displayOrder) FROM EmployeeEntity b WHERE b.active = true")
    Optional<Integer> findMaxDisplayOrderByActiveTrue();

    @Override
    List<EmployeeEntity> findByActiveTrueAndDisplayOrderGreaterThanEqual(Integer order);

    @Override
    List<EmployeeEntity> findByActiveTrueAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(Integer order);

    @Override
    List<EmployeeEntity> findByActiveTrueAndDisplayOrderBetween(Integer start, Integer end);

    Optional<EmployeeEntity> findByIdAndActiveTrue(UUID id);

    /**
     * Finds active employee by id and eagerly loads supported treatments.
     *
     * @param id employee id
     * @return optional employee with treatments
     */
    @Query("""
            SELECT e
            FROM EmployeeEntity e
            LEFT JOIN FETCH e.providedTreatments
            WHERE e.id = :id AND e.active = true
            """)
    Optional<EmployeeEntity> findByIdAndActiveTrueWithTreatments(@Param("id") UUID id);

    /**
     * Locks all active employees for write in display order.
     * Used to serialize admin ordering operations and avoid conflicts.
     *
     * @return locked list of active employees
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM EmployeeEntity b WHERE b.active = true ORDER BY b.displayOrder ASC")
    List<EmployeeEntity> findAllActiveForUpdateOrderByDisplayOrderAsc();

    /**
     * Finds active employee by id with pessimistic write lock.
     * Used to serialize booking creation per employee and prevent double-booking
     * race conditions.
     *
     * @param id employee id
     * @return optional active employee entity
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM EmployeeEntity b WHERE b.id = :id AND b.active = true")
    Optional<EmployeeEntity> findByIdAndActiveTrueForUpdate(@Param("id") UUID id);

    /**
     * Checks whether the employee can provide the treatment.
     *
     * @param employeeId  employee id
     * @param treatmentId treatment id
     * @return {@code true} when the active employee is linked to the active
     *         treatment
     */
    @Query("""
            SELECT COUNT(e) > 0
            FROM EmployeeEntity e
            JOIN e.providedTreatments t
            WHERE e.id = :employeeId
              AND e.active = true
              AND t.id = :treatmentId
              AND t.active = true
            """)
    boolean existsActiveEmployeeTreatment(
            @Param("employeeId") UUID employeeId,
            @Param("treatmentId") UUID treatmentId);
}
