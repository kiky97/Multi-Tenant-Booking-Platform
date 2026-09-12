package com.booking.engine.service;

import com.booking.engine.entity.DisplayOrderedEntity;
import com.booking.engine.repository.DisplayOrderRepository;

/**
 * Service contract for display order operations.
 * Defines display order related business operations.
 */
public interface DisplayOrderService {

    /**
     * Resolves the persisted display order for a newly created entity.
     *
     * @param requestedOrder requested display order
     * @param repository     repository instance
     * @return resolved display order
     */
    <T extends DisplayOrderedEntity> Integer resolveDisplayOrder(
            Integer requestedOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Resolves the persisted display order for an entity update.
     *
     * @param requestedOrder requested display order
     * @param currentOrder   current display order
     * @param repository     repository instance
     * @return resolved display order
     */
    <T extends DisplayOrderedEntity> Integer resolveDisplayOrderForUpdate(
            Integer requestedOrder,
            Integer currentOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Validates display order for create.
     *
     * @param order      display order
     * @param repository repository instance
     */
    <T extends DisplayOrderedEntity> void validateDisplayOrderForCreate(
            Integer order,
            DisplayOrderRepository<T> repository);

    /**
     * Validates display order for update.
     *
     * @param order      display order
     * @param repository repository instance
     */
    <T extends DisplayOrderedEntity> void validateDisplayOrderForUpdate(
            Integer order,
            DisplayOrderRepository<T> repository);

    /**
     * Shifts active display orders starting from the provided order.
     *
     * @param fromOrder  from order value
     * @param repository repository instance
     */
    <T extends DisplayOrderedEntity> void shiftDisplayOrders(
            Integer fromOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Moves an entity from one display order to another.
     *
     * @param oldOrder   previous display order
     * @param newOrder   new display order
     * @param repository repository instance
     */
    <T extends DisplayOrderedEntity> void moveDisplayOrder(
            Integer oldOrder,
            Integer newOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Closes the ordering gap after an entity is deleted.
     *
     * @param removedOrder removed display order
     * @param repository   repository instance
     */
    <T extends DisplayOrderedEntity> void shiftOrdersAfterDelete(
            Integer removedOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Resolves temporary display order.
     *
     * @param repository repository instance
     * @return temporary display order value
     */
    <T extends DisplayOrderedEntity> Integer resolveTemporaryDisplayOrder(
            DisplayOrderRepository<T> repository);

    /**
     * Locks active ordering scope.
     *
     * @param repository repository instance
     */
    <T extends DisplayOrderedEntity> void lockActiveOrderingScope(
            DisplayOrderRepository<T> repository);
}
