package com.booking.engine.service.impl;

import com.booking.engine.entity.DisplayOrderedEntity;
import com.booking.engine.repository.DisplayOrderRepository;
import com.booking.engine.service.DisplayOrderService;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link DisplayOrderService}.
 * Provides display order related business operations.
 */
@Service
public class DisplayOrderServiceImpl implements DisplayOrderService {
    // ---------------------- Constants ----------------------

    private static final String DISPLAY_ORDER_TOO_HIGH_PREFIX = "Display order cannot be greater than ";

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DisplayOrderedEntity> Integer resolveDisplayOrder(
            Integer requestedOrder,
            DisplayOrderRepository<T> repository) {

        if (requestedOrder == null) {
            return repository.findMaxDisplayOrderByActiveTrue()
                    .map(max -> max + 1)
                    .orElse(0);
        }

        validateDisplayOrderForCreate(requestedOrder, repository);
        return requestedOrder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DisplayOrderedEntity> Integer resolveDisplayOrderForUpdate(
            Integer requestedOrder,
            Integer currentOrder,
            DisplayOrderRepository<T> repository) {

        Integer resolvedOrder = requestedOrder == null
                ? currentOrder
                : requestedOrder;

        validateDisplayOrderForUpdate(resolvedOrder, repository);
        return resolvedOrder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DisplayOrderedEntity> void validateDisplayOrderForCreate(
            Integer order,
            DisplayOrderRepository<T> repository) {

        Integer max = repository.findMaxDisplayOrderByActiveTrue().orElse(-1);

        if (order > max + 1) {
            throw new IllegalArgumentException(
                    DISPLAY_ORDER_TOO_HIGH_PREFIX + (max + 1));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DisplayOrderedEntity> void validateDisplayOrderForUpdate(
            Integer order,
            DisplayOrderRepository<T> repository) {

        Integer max = repository.findMaxDisplayOrderByActiveTrue().orElse(-1);

        if (order > max) {
            throw new IllegalArgumentException(
                    DISPLAY_ORDER_TOO_HIGH_PREFIX + max);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DisplayOrderedEntity> void shiftDisplayOrders(
            Integer fromOrder,
            DisplayOrderRepository<T> repository) {

        List<T> toShift = repository.findByActiveTrueAndDisplayOrderGreaterThanEqual(fromOrder);

        toShift.stream()
                .sorted(Comparator.comparing(DisplayOrderedEntity::getDisplayOrder).reversed())
                .forEach(entity -> entity.setDisplayOrder(entity.getDisplayOrder() + 1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DisplayOrderedEntity> void moveDisplayOrder(
            Integer oldOrder,
            Integer newOrder,
            DisplayOrderRepository<T> repository) {

        if (oldOrder.equals(newOrder)) {
            return;
        }

        if (oldOrder < newOrder) {
            List<T> entities = repository.findByActiveTrueAndDisplayOrderBetween(oldOrder + 1, newOrder);
            entities.forEach(entity -> entity.setDisplayOrder(entity.getDisplayOrder() - 1));
        } else {
            List<T> entities = repository.findByActiveTrueAndDisplayOrderBetween(newOrder, oldOrder - 1);
            entities.forEach(entity -> entity.setDisplayOrder(entity.getDisplayOrder() + 1));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DisplayOrderedEntity> void shiftOrdersAfterDelete(
            Integer removedOrder,
            DisplayOrderRepository<T> repository) {

        List<T> toShift = repository.findByActiveTrueAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(removedOrder);
        toShift.forEach(entity -> entity.setDisplayOrder(entity.getDisplayOrder() - 1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DisplayOrderedEntity> Integer resolveTemporaryDisplayOrder(
            DisplayOrderRepository<T> repository) {

        Optional<Integer> max = repository.findMaxDisplayOrderByActiveTrue();
        return max.map(value -> value + 1).orElse(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DisplayOrderedEntity> void lockActiveOrderingScope(
            DisplayOrderRepository<T> repository) {

        repository.findAllActiveForUpdateOrderByDisplayOrderAsc();
    }
}
