package com.booking.engine.entity;

/**
 * Contract for entities that participate in active display ordering.
 * Used by shared ordering services to manage insert and reorder operations.
 */
public interface DisplayOrderedEntity {

    Integer getDisplayOrder();

    void setDisplayOrder(Integer displayOrder);

    Boolean getActive();
}
