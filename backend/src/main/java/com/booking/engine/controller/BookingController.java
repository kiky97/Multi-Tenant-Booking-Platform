package com.booking.engine.controller;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingCheckoutValidationRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.PublicBookingHoldResponseDto;
import com.booking.engine.dto.PublicBookingSummaryResponseDto;
import com.booking.engine.security.ClientIpResolver;
import com.booking.engine.security.PublicBookingActionRateLimitService;
import com.booking.engine.security.PublicBookingActionRateLimitService.Action;
import com.booking.engine.service.PublicBookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST controller for booking operations.
 * Handles public booking creation, checkout, retrieval, and temporary hold
 * cancellation.
 */
@RestController
@RequestMapping(value = "/api/v1/public/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class BookingController {

    private static final int MAX_DEVICE_ID_LENGTH = 128;
    private static final String HOLD_ACCESS_TOKEN_HEADER = "X-Booking-Hold-Access-Token";
    private static final String DEVICE_ID_HEADER = "X-Booking-Device-Id";

    private final PublicBookingService bookingService;
    private final ClientIpResolver clientIpResolver;
    private final PublicBookingActionRateLimitService actionRateLimitService;

    /**
     * Retrieves booking by ID.
     *
     * @param id the booking ID
     * @return public-safe booking status summary
     */
    @GetMapping("/{id}")
    public ResponseEntity<PublicBookingSummaryResponseDto> getBookingById(
            @PathVariable @NotNull UUID id,
            @RequestHeader(value = HOLD_ACCESS_TOKEN_HEADER, required = false) String holdAccessToken) {
        PublicBookingSummaryResponseDto booking = bookingService.getBookingById(id, holdAccessToken);
        return ResponseEntity.ok(booking);
    }

    /**
     * Creates a new booking with immediate Stripe payment.
     *
     * @param request the booking request
     * @return created booking with 201 status
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponseDto> createBooking(
            @Valid @RequestBody BookingRequestDto request,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String clientDeviceId,
            HttpServletRequest httpServletRequest) {

        rateLimitAction(Action.DIRECT_CREATE, httpServletRequest, null, clientDeviceId);
        BookingResponseDto created = bookingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Creates a temporary hold for a selected slot.
     *
     * @param request the slot hold request
     * @return pending booking with expiration timestamp
     */
    @PostMapping(value = "/hold", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublicBookingHoldResponseDto> holdBookingSlot(
            @Valid @RequestBody BookingHoldRequestDto request,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String clientDeviceId,
            HttpServletRequest httpServletRequest) {

        PublicBookingHoldResponseDto heldBooking = bookingService.holdSlot(
                request,
                clientIpResolver.resolve(httpServletRequest),
                sanitizeClientDeviceId(clientDeviceId));
        return ResponseEntity.status(HttpStatus.CREATED).body(heldBooking);
    }

    /**
     * Validates a held booking before Stripe checkout is opened, so blocked
     * customers see the error immediately when pressing Confirm Booking.
     *
     * @param id      booking identifier
     * @param request customer details to validate
     * @return 204 No Content when checkout may proceed
     */
    @PostMapping(value = "/{id}/checkout/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> validateHeldBookingCheckout(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody BookingCheckoutValidationRequestDto request,
            @RequestHeader(value = HOLD_ACCESS_TOKEN_HEADER, required = false) String holdAccessToken,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String clientDeviceId,
            HttpServletRequest httpServletRequest) {

        rateLimitAction(Action.CHECKOUT_VALIDATE, httpServletRequest, id, clientDeviceId);
        bookingService.validateHeldBookingCheckout(id, request, holdAccessToken);
        return ResponseEntity.noContent().build();
    }

    /**
     * Prepares Stripe checkout for a previously held booking.
     *
     * @param id      booking identifier
     * @param request checkout payload with customer details
     * @return PaymentIntent client secret and status
     */
    @PostMapping(value = "/{id}/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingCheckoutSessionResponseDto> prepareHeldBookingCheckout(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody BookingCheckoutSessionRequestDto request,
            @RequestHeader(value = HOLD_ACCESS_TOKEN_HEADER, required = false) String holdAccessToken,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String clientDeviceId,
            HttpServletRequest httpServletRequest) {

        rateLimitAction(Action.CHECKOUT_PREPARE, httpServletRequest, id, clientDeviceId);
        BookingCheckoutSessionResponseDto checkoutSession = bookingService.prepareHeldBookingCheckout(
                id,
                request,
                holdAccessToken);
        return ResponseEntity.ok(checkoutSession);
    }

    /**
     * Finalizes a previously held booking after Stripe payment.
     * The booking is confirmed immediately once the backend verifies Stripe
     * reported a successful PaymentIntent, while the webhook remains a fallback
     * synchronization channel.
     *
     * @param id      booking identifier
     * @param request booking confirmation payload
     * @return latest booking state after checkout confirmation
     */
    @PostMapping(value = "/{id}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponseDto> confirmHeldBooking(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody BookingConfirmationRequestDto request,
            @RequestHeader(value = HOLD_ACCESS_TOKEN_HEADER, required = false) String holdAccessToken,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String clientDeviceId,
            HttpServletRequest httpServletRequest) {

        rateLimitAction(Action.CONFIRM, httpServletRequest, id, clientDeviceId);
        BookingResponseDto confirmed = bookingService.confirmHeldBooking(id, request, holdAccessToken);
        return ResponseEntity.ok(confirmed);
    }

    /**
     * Cancels an unpaid temporary booking hold.
     *
     * @param id the booking ID to cancel
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable @NotNull UUID id,
            @RequestHeader(value = HOLD_ACCESS_TOKEN_HEADER, required = false) String holdAccessToken,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String clientDeviceId,
            HttpServletRequest httpServletRequest) {
        rateLimitAction(Action.CANCEL, httpServletRequest, id, clientDeviceId);
        bookingService.cancelBooking(id, holdAccessToken);
        return ResponseEntity.noContent().build();
    }

    private void rateLimitAction(
            Action action,
            HttpServletRequest httpServletRequest,
            UUID bookingOrHoldId,
            String clientDeviceId) {

        actionRateLimitService.registerAttempt(
                action,
                clientIpResolver.resolve(httpServletRequest),
                bookingOrHoldId,
                sanitizeClientDeviceId(clientDeviceId));
    }

    private String sanitizeClientDeviceId(String clientDeviceId) {
        String cleaned = cleanOptionalValue(clientDeviceId);
        if (cleaned == null) {
            return null;
        }

        return cleaned.length() > MAX_DEVICE_ID_LENGTH
                ? cleaned.substring(0, MAX_DEVICE_ID_LENGTH)
                : cleaned;
    }

    private String cleanOptionalValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isBlank() || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }

        return trimmed;
    }
}
