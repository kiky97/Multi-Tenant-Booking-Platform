package com.booking.engine.exception;

/**
 * Raised when an endpoint rate limit is exceeded.
 */
public class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String clientMessage;

    public RateLimitExceededException(String message) {
        super(message);
        this.clientMessage = message;
    }

    public RateLimitExceededException(String logMessage, String clientMessage) {
        super(logMessage);
        this.clientMessage = clientMessage;
    }

    public String getClientMessage() {
        return clientMessage;
    }
}
