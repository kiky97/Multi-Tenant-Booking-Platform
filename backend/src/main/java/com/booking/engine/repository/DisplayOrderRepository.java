package com.booking.engine.repository;

import com.booking.engine.entity.DisplayOrderedEntity;
import java.util.List;
import java.util.Optional;

/**
 * Shared repository contract for entities that support active display ordering.
 *
 * @param <T> ordered entity type
 */
public interface DisplayOrderRepository<T extends DisplayOrderedEntity> {

    Optional<Integer> findMaxDisplayOrderByActiveTrue();

    List<T> findByActiveTrueAndDisplayOrderGreaterThanEqual(Integer order);

    List<T> findByActiveTrueAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(Integer order);

    List<T> findByActiveTrueAndDisplayOrderBetween(Integer start, Integer end);

    /**
     * Locks all active ordered entities for write in ascending display order.
     * Used to serialize admin ordering operations and avoid conflicts.
     *
     * @return locked active entities
     */
    List<T> findAllActiveForUpdateOrderByDisplayOrderAsc();
}
