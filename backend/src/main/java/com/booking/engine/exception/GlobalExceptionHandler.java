package com.booking.engine.exception;

import com.booking.engine.security.AuthSecurityMessages;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralizes REST API exception-to-response mapping.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_NOT_FOUND = "Not Found";
    private static final String ERROR_VALIDATION = "Validation Error";
    private static final String ERROR_BAD_REQUEST = "Bad Request";
    private static final String ERROR_CONFLICT = "Conflict";
    private static final String ERROR_UNAUTHORIZED = "Unauthorized";
    private static final String ERROR_FORBIDDEN = "Forbidden";
    private static final String ERROR_TOO_MANY_REQUESTS = "Too Many Requests";
    private static final String ERROR_INTERNAL = "Internal Server Error";
    private static final String ILLEGAL_STATE_CLIENT_MESSAGE = "Request could not be completed in its current state.";
    private static final String ILLEGAL_ARGUMENT_CLIENT_MESSAGE = "Request could not be processed. Please review the submitted data and try again.";

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=entity_not_found path={} message={}",
                request.getRequestURI(),
                sanitizeLogMessage(ex.getMessage()));

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                ERROR_NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=method_argument_not_valid path={} fieldErrorCount={}",
                request.getRequestURI(),
                ex.getBindingResult().getFieldErrorCount());
        log.debug("event=exception_details type=method_argument_not_valid path={} message={}",
                request.getRequestURI(),
                sanitizeLogMessage(ex.getMessage()));

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        this::toFriendlyFieldMessage,
                        (existing, replacement) -> existing));

        String message = fieldErrors.size() == 1
                ? fieldErrors.values().iterator().next()
                : "Please review the highlighted fields and try again.";

        ValidationErrorResponse response = new ValidationErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_VALIDATION,
                message,
                fieldErrors,
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=http_message_not_readable path={}", request.getRequestURI());
        log.debug("event=exception_details type=http_message_not_readable path={} message={}",
                request.getRequestURI(),
                sanitizeLogMessage(ex.getMessage()));

        String message = "Some submitted values could not be read. Please review the form and try again.";
        InvalidJsonValue invalidValue = findInvalidJsonValue(ex);

        if (invalidValue != null) {
            String fieldName = invalidValue.fieldName();
            String fieldLabel = toFieldLabel(fieldName);
            Class<?> targetType = invalidValue.targetType();

            if (LocalDate.class.equals(targetType)) {
                message = fieldName != null
                        ? "Please choose a real calendar date for " + fieldLabel + "."
                        : "Please choose a real calendar date and try again.";
            } else if (LocalTime.class.equals(targetType)) {
                message = fieldName != null
                        ? "Please enter a valid time for " + fieldLabel + "."
                        : "Please enter a valid time and try again.";
            } else {
                message = fieldName != null
                        ? fieldLabel + " contains an invalid value."
                        : "One or more submitted values are invalid. Please review the form and try again.";
            }
        }

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                message,
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=constraint_violation path={}", request.getRequestURI());
        log.debug("event=exception_details type=constraint_violation path={} message={}",
                request.getRequestURI(),
                sanitizeLogMessage(ex.getMessage()));

        String message = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_VALIDATION,
                message,
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(BookingValidationException.class)
    public ResponseEntity<ErrorResponse> handleBookingValidation(
            BookingValidationException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=booking_validation path={}", request.getRequestURI());
        log.debug("event=exception_details type=booking_validation path={} message={}",
                request.getRequestURI(),
                sanitizeLogMessage(ex.getMessage()));

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                ex.getClientMessage(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePaymentProcessing(
            PaymentProcessingException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=payment_processing path={} reason={}",
                request.getRequestURI(),
                ex.getClass().getSimpleName());
        log.debug("event=exception_details type=payment_processing path={} cause={}",
                request.getRequestURI(),
                ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : "none");

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                ex.getClientMessage(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=illegal_state path={} message={}",
                request.getRequestURI(), sanitizeLogMessage(ex.getMessage()));

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                ILLEGAL_STATE_CLIENT_MESSAGE,
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=illegal_argument path={} message={}",
                request.getRequestURI(), sanitizeLogMessage(ex.getMessage()));

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                ERROR_BAD_REQUEST,
                ILLEGAL_ARGUMENT_CLIENT_MESSAGE,
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        Throwable mostSpecificCause = ex.getMostSpecificCause();
        String causeMessage = mostSpecificCause != null
                ? mostSpecificCause.getMessage()
                : ex.getMessage();

        log.warn("event=exception_handled type=data_integrity path={}", request.getRequestURI());
        log.debug("event=exception_details type=data_integrity path={} reason={}",
                request.getRequestURI(),
                mostSpecificCause != null ? mostSpecificCause.getClass().getSimpleName()
                        : ex.getClass().getSimpleName());

        String friendly = "Request conflicts with existing data constraints";
        String lower = causeMessage != null ? causeMessage.toLowerCase() : "";
        if (lower.contains("display_order") || lower.contains("employee_display_order")) {
            friendly = "Display order is already in use by another employee. Please choose a different position.";
        }

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                ERROR_CONFLICT,
                friendly,
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=authentication path={} reason={}",
                request.getRequestURI(),
                ex.getClass().getSimpleName());
        String message = isLoginRequest(request)
                ? AuthSecurityMessages.INVALID_CREDENTIALS
                : AuthSecurityMessages.AUTHENTICATION_FAILED;

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                ERROR_UNAUTHORIZED,
                message,
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=rate_limit path={} reason={}",
                request.getRequestURI(),
                ex.getClass().getSimpleName());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ERROR_TOO_MANY_REQUESTS,
                isLoginRequest(request) ? AuthSecurityMessages.TOO_MANY_LOGIN_ATTEMPTS : ex.getClientMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("event=exception_handled type=access_denied path={} reason={}",
                request.getRequestURI(),
                ex.getClass().getSimpleName());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                ERROR_FORBIDDEN,
                ERROR_FORBIDDEN,
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("event=exception_handled type=unexpected path={} reason={} message={}",
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                sanitizeLogMessage(ex.getMessage()));

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ERROR_INTERNAL,
                "An unexpected error occurred",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String toFriendlyFieldMessage(FieldError error) {
        String fieldLabel = toFieldLabel(error.getField());
        String rawMessage = error.getDefaultMessage();

        if (rawMessage == null || rawMessage.isBlank()) {
            return fieldLabel + " is invalid.";
        }

        String normalizedMessage = rawMessage.trim();
        String lowerCaseMessage = normalizedMessage.toLowerCase(Locale.ROOT);

        if (lowerCaseMessage.equals("must not be null")
                || lowerCaseMessage.equals("must not be blank")
                || lowerCaseMessage.equals("must not be empty")) {
            return fieldLabel + " is required.";
        }

        if (lowerCaseMessage.startsWith("must be greater than or equal to ")) {
            String minValue = normalizedMessage.substring("must be greater than or equal to ".length());
            return fieldLabel + " must be " + minValue + " or greater.";
        }

        if (lowerCaseMessage.startsWith("must be less than or equal to ")) {
            String maxValue = normalizedMessage.substring("must be less than or equal to ".length());
            return fieldLabel + " must be " + maxValue + " or less.";
        }

        if (lowerCaseMessage.startsWith("size must be between ")) {
            return fieldLabel + " length is outside the allowed range.";
        }

        return normalizedMessage;
    }

    private InvalidJsonValue findInvalidJsonValue(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof InvalidFormatException formatException) {
                String fieldName = formatException.getPath().isEmpty()
                        ? null
                        : formatException.getPath().get(formatException.getPath().size() - 1).getFieldName();
                return new InvalidJsonValue(fieldName, formatException.getTargetType());
            }
            if (current instanceof tools.jackson.databind.exc.InvalidFormatException formatException) {
                String fieldName = formatException.getPath().isEmpty()
                        ? null
                        : formatException.getPath().get(formatException.getPath().size() - 1).getPropertyName();
                return new InvalidJsonValue(fieldName, formatException.getTargetType());
            }
            current = current.getCause();
        }

        return null;
    }

    private String toFieldLabel(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return "This field";
        }

        String simplified = fieldName.replaceAll(".*\\.", "").replaceAll("\\[[^\\]]*\\]", "");
        simplified = simplified.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);

        if (simplified.isEmpty()) {
            return "This field";
        }

        return Character.toUpperCase(simplified.charAt(0)) + simplified.substring(1);
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return request != null && "/api/v1/public/auth/login".equals(request.getRequestURI());
    }

    private String sanitizeLogMessage(String message) {
        return SensitiveLogSanitizer.sanitizeForLogs(message);
    }

    private record InvalidJsonValue(String fieldName, Class<?> targetType) {
    }
}
