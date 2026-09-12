package com.booking.engine.exception;

import java.time.LocalDateTime;

/**
 * Standardized error response for general errors.
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param error     error type
 * @param message   error message
 * @param path      request path that caused the error
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {

    public ErrorResponse(int status, String error, String message) {
        this(LocalDateTime.now(), status, error, message, null);
    }
}
