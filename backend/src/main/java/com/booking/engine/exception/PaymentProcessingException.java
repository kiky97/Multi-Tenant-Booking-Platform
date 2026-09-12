package com.booking.engine.exception;

/**
 * Exception thrown when a payment operation fails.
 */
public class PaymentProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String clientMessage;

    public PaymentProcessingException(String message) {
        super(message);
        this.clientMessage = message;
    }

    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.clientMessage = message;
    }

    public PaymentProcessingException(String clientMessage, String internalMessage, Throwable cause) {
        super(internalMessage, cause);
        this.clientMessage = clientMessage;
    }

    public String getClientMessage() {
        return clientMessage;
    }
}
