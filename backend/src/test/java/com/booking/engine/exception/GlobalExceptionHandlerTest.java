package com.booking.engine.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/api/v1/test");
    }

    @Test
    void handlesEntityNotFound() {
        EntityNotFoundException exception = new EntityNotFoundException("Employee",
                UUID.fromString("11111111-1111-1111-1111-111111111111"));

        ErrorResponse response = handler.handleEntityNotFound(exception, request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.error()).isEqualTo("Not Found");
        assertThat(response.message()).contains("Employee not found");
        assertThat(response.path()).isEqualTo("/api/v1/test");
    }

    @Test
    void handlesValidationErrorsAndKeepsFirstDuplicateFieldMessage() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "payload");
        bindingResult.addError(new FieldError("payload", "name", "Name is required"));
        bindingResult.addError(new FieldError("payload", "name", "Replacement should be ignored"));
        bindingResult.addError(new FieldError("payload", "email", "Email is invalid"));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleValidatedMethod", Object.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(new MethodParameter(method, 0),
                bindingResult);

        ValidationErrorResponse response = handler.handleValidationErrors(exception, request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("Validation Error");
        assertThat(response.message()).isEqualTo("Please review the highlighted fields and try again.");
        assertThat(response.fieldErrors()).containsEntry("name", "Name is required");
        assertThat(response.fieldErrors()).containsEntry("email", "Email is invalid");
        assertThat(response.path()).isEqualTo("/api/v1/test");
    }

    @Test
    void handlesValidationErrorsWithFriendlyDefaultMessages() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "payload");
        bindingResult.addError(new FieldError("payload", "displayOrder", "must not be null"));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleValidatedMethod", Object.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(new MethodParameter(method, 0),
                bindingResult);

        ValidationErrorResponse response = handler.handleValidationErrors(exception, request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.message()).isEqualTo("Display order is required.");
        assertThat(response.fieldErrors()).containsEntry("displayOrder", "Display order is required.");
    }

    @Test
    void handlesConstraintViolations() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = Mockito.mock(ConstraintViolation.class);
        Path propertyPath = Mockito.mock(Path.class);
        Mockito.when(propertyPath.toString()).thenReturn("bookingDate");
        Mockito.when(violation.getPropertyPath()).thenReturn(propertyPath);
        Mockito.when(violation.getMessage()).thenReturn("must be today or in the future");

        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ErrorResponse response = handler.handleConstraintViolation(exception, request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("Validation Error");
        assertThat(response.message()).contains("bookingDate: must be today or in the future");
    }

    @Test
    void handlesBookingAndPaymentValidationErrors() {
        ErrorResponse bookingResponse = handler.handleBookingValidation(
                new BookingValidationException("Slot is not available"), request).getBody();
        ErrorResponse paymentResponse = handler.handlePaymentProcessing(
                new PaymentProcessingException(
                        "Payment could not be completed. Please try again.",
                        "Stripe payment failed: provider detail",
                        new RuntimeException("provider detail")),
                request).getBody();

        assertThat(bookingResponse).isNotNull();
        assertThat(bookingResponse.error()).isEqualTo("Bad Request");
        assertThat(bookingResponse.message()).isEqualTo("Slot is not available");

        assertThat(paymentResponse).isNotNull();
        assertThat(paymentResponse.error()).isEqualTo("Bad Request");
        assertThat(paymentResponse.message()).isEqualTo("Payment could not be completed. Please try again.");
        assertThat(paymentResponse.message()).doesNotContain("provider detail");
    }

    @Test
    void handlesIllegalStateAndIllegalArgument() {
        ErrorResponse stateResponse = handler.handleIllegalState(
                new IllegalStateException("State issue"), request).getBody();
        ErrorResponse argumentResponse = handler.handleIllegalArgument(
                new IllegalArgumentException("Argument issue"), request).getBody();

        assertThat(stateResponse).isNotNull();
        assertThat(stateResponse.status()).isEqualTo(400);
        assertThat(stateResponse.error()).isEqualTo("Bad Request");
        assertThat(stateResponse.message()).isEqualTo("Request could not be completed in its current state.");
        assertThat(stateResponse.message()).doesNotContain("State issue");
        assertThat(argumentResponse).isNotNull();
        assertThat(argumentResponse.status()).isEqualTo(400);
        assertThat(argumentResponse.error()).isEqualTo("Bad Request");
        assertThat(argumentResponse.message())
                .isEqualTo("Request could not be processed. Please review the submitted data and try again.");
        assertThat(argumentResponse.message()).doesNotContain("Argument issue");
    }

    @Test
    void handlesDataIntegrityViolationWithFriendlyConflictMessages() {
        DataIntegrityViolationException displayOrderConflict = new DataIntegrityViolationException(
                "constraint",
                new RuntimeException("duplicate key value violates unique constraint on display_order"));

        DataIntegrityViolationException genericConflict = new DataIntegrityViolationException(
                "constraint",
                new RuntimeException("some other conflict"));

        ErrorResponse displayOrderResponse = handler.handleDataIntegrityViolation(displayOrderConflict, request)
                .getBody();
        ErrorResponse genericResponse = handler.handleDataIntegrityViolation(genericConflict, request).getBody();

        assertThat(displayOrderResponse).isNotNull();
        assertThat(displayOrderResponse.status()).isEqualTo(409);
        assertThat(displayOrderResponse.error()).isEqualTo("Conflict");
        assertThat(displayOrderResponse.message()).contains("Display order is already in use");

        assertThat(genericResponse).isNotNull();
        assertThat(genericResponse.message()).isEqualTo("Request conflicts with existing data constraints");
    }

    @Test
    void handlesAuthenticationAccessDeniedAndUnexpectedExceptions() {
        ErrorResponse authResponse = handler.handleAuthentication(
                new BadCredentialsException("Invalid credentials"), request).getBody();
        ErrorResponse accessDeniedResponse = handler.handleAccessDenied(
                new AccessDeniedException("Forbidden area"), request).getBody();
        ErrorResponse genericResponse = handler.handleGenericException(
                new Exception("Unexpected boom"), request).getBody();

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.status()).isEqualTo(401);
        assertThat(authResponse.error()).isEqualTo("Unauthorized");
        assertThat(authResponse.message()).isEqualTo("Authentication failed");

        assertThat(accessDeniedResponse).isNotNull();
        assertThat(accessDeniedResponse.status()).isEqualTo(403);
        assertThat(accessDeniedResponse.error()).isEqualTo("Forbidden");
        assertThat(accessDeniedResponse.message()).isEqualTo("Forbidden");

        assertThat(genericResponse).isNotNull();
        assertThat(genericResponse.status()).isEqualTo(500);
        assertThat(genericResponse.error()).isEqualTo("Internal Server Error");
        assertThat(genericResponse.message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void handlesLoginAuthenticationAndRateLimitWithNeutralMessages() {
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/v1/public/auth/login");

        ErrorResponse authResponse = handler.handleAuthentication(
                new BadCredentialsException("Admin user not found: admin"), loginRequest).getBody();
        ErrorResponse rateLimitResponse = handler.handleRateLimitExceeded(
                new RateLimitExceededException("too many"), loginRequest).getBody();

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.message()).isEqualTo("Invalid username or password");

        assertThat(rateLimitResponse).isNotNull();
        assertThat(rateLimitResponse.status()).isEqualTo(429);
        assertThat(rateLimitResponse.error()).isEqualTo("Too Many Requests");
        assertThat(rateLimitResponse.message()).isEqualTo("Too many login attempts. Please try again later.");
    }

    @Test
    void handlesPublicHoldRateLimitWithHoldMessage() {
        MockHttpServletRequest holdRequest = new MockHttpServletRequest("POST", "/api/v1/public/bookings/hold");

        ErrorResponse response = handler.handleRateLimitExceeded(
                new RateLimitExceededException(
                        "public hold rate limit exceeded",
                        "Too many appointment hold attempts. Please wait a moment and try again."),
                holdRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(429);
        assertThat(response.message())
                .isEqualTo("Too many appointment hold attempts. Please wait a moment and try again.");
    }

    @Test
    void sanitizeLogMessageRedactsCustomerContactData() throws Exception {
        Method method = GlobalExceptionHandler.class.getDeclaredMethod("sanitizeLogMessage", String.class);
        method.setAccessible(true);

        String sanitized = (String) method.invoke(
                handler,
                "rejected value [john.doe@example.com] and phone [+353 87 123 4567]");

        assertThat(sanitized).contains("[emailMask=j***e@example.com]");
        assertThat(sanitized).contains("[phoneHash=");
        assertThat(sanitized).doesNotContain("john.doe@example.com");
        assertThat(sanitized).doesNotContain("+353 87 123 4567");
    }

    private void sampleValidatedMethod(Object ignored) {
    }
}
