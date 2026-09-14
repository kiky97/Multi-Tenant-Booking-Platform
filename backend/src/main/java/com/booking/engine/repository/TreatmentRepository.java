package com.booking.engine.repository;

import com.booking.engine.entity.Service;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for {@link Service}. */
@Repository
public interface TreatmentRepository extends JpaRepository<Service, UUID>,
        DisplayOrderRepository<Service> {

    List<Service> findAllByActiveTrueOrderByDisplayOrderAsc();

    @Override
    @Query("SELECT MAX(t.displayOrder) FROM TreatmentEntity t WHERE t.active = true")
    Optional<Integer> findMaxDisplayOrderByActiveTrue();

    @Override
    List<Service> findByActiveTrueAndDisplayOrderGreaterThanEqual(Integer order);

    @Override
    List<Service> findByActiveTrueAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(Integer order);

    @Override
    List<Service> findByActiveTrueAndDisplayOrderBetween(Integer start, Integer end);

    Optional<Service> findByIdAndActiveTrue(UUID id);

    List<Service> findAllByIdInAndActiveTrue(Set<UUID> ids);

    /**
     * Finds active treatment by id with pessimistic write lock.
     * Used to serialize booking creation per treatment and prevent double-booking
     * race conditions.
     *
     * @param id treatment id
     * @return optional active treatment entity
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TreatmentEntity t WHERE t.id = :id AND t.active = true")
    Optional<Service> findByIdAndActiveTrueForUpdate(@Param("id") UUID id);

    /**
     * Locks all active treatments for write in display order.
     * Used to serialize admin ordering operations and avoid conflicts.
     *
     * @return locked list of active treatments
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TreatmentEntity t WHERE t.active = true ORDER BY t.displayOrder ASC")
    List<Service> findAllActiveForUpdateOrderByDisplayOrderAsc();
}
