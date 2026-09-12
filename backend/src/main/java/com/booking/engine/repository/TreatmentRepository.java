package com.booking.engine.repository;

import com.booking.engine.entity.TreatmentEntity;
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

/** Repository for {@link TreatmentEntity}. */
@Repository
public interface TreatmentRepository extends JpaRepository<TreatmentEntity, UUID>,
        DisplayOrderRepository<TreatmentEntity> {

    List<TreatmentEntity> findAllByActiveTrueOrderByDisplayOrderAsc();

    @Override
    @Query("SELECT MAX(t.displayOrder) FROM TreatmentEntity t WHERE t.active = true")
    Optional<Integer> findMaxDisplayOrderByActiveTrue();

    @Override
    List<TreatmentEntity> findByActiveTrueAndDisplayOrderGreaterThanEqual(Integer order);

    @Override
    List<TreatmentEntity> findByActiveTrueAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(Integer order);

    @Override
    List<TreatmentEntity> findByActiveTrueAndDisplayOrderBetween(Integer start, Integer end);

    Optional<TreatmentEntity> findByIdAndActiveTrue(UUID id);

    List<TreatmentEntity> findAllByIdInAndActiveTrue(Set<UUID> ids);

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
    Optional<TreatmentEntity> findByIdAndActiveTrueForUpdate(@Param("id") UUID id);

    /**
     * Locks all active treatments for write in display order.
     * Used to serialize admin ordering operations and avoid conflicts.
     *
     * @return locked list of active treatments
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TreatmentEntity t WHERE t.active = true ORDER BY t.displayOrder ASC")
    List<TreatmentEntity> findAllActiveForUpdateOrderByDisplayOrderAsc();
}
