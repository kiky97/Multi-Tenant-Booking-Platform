package com.booking.engine.exception;

import java.util.UUID;

/**
 * Exception thrown when requested entity is not found in the database.
 */
public class EntityNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EntityNotFoundException(String entityName, UUID id) {
        super(String.format("%s not found with id=%s", entityName, id));
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
