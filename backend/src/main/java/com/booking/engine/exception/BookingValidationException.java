package com.booking.engine.exception;

/**
 * Exception thrown when booking validation fails.
 * Used for business validation errors that can be safely shown to clients.
 */
public class BookingValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String clientMessage;

    public BookingValidationException(String message) {
        super(message);
        this.clientMessage = message;
    }

    public BookingValidationException(String message, Throwable cause) {
        super(message, cause);
        this.clientMessage = message;
    }

    public String getClientMessage() {
        return clientMessage;
    }
}
