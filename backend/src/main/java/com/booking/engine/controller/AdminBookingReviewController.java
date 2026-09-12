package com.booking.engine.controller;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryResponseDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.service.AdminBookingService;
import com.booking.engine.service.BookingBlacklistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for appointment management and booking blacklist tools.
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping(value = "/api/v1/admin/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminBookingReviewController {

    private static final String ADMIN_HOLD_SESSION_HEADER = "X-Admin-Hold-Session-Id";

    private final AdminBookingService bookingService;
    private final BookingBlacklistService bookingBlacklistService;

    /**
     * Returns admin booking overview with optional search by customer contact data.
     *
     * @param search customer name / phone / email query
     * @return ordered booking list plus counters
     */
    @GetMapping
    public AdminBookingListResponseDto getAdminBookings(
            @RequestParam(required = false) String search) {
        return bookingService.getAdminBookings(search);
    }

    /**
     * Returns the latest known customer details for an exact phone-number match.
     *
     * @param phone raw phone number entered by the admin
     * @return latest matching customer details or no content
     */
    @GetMapping("/customer-lookup")
    public ResponseEntity<AdminBookingCustomerLookupResponseDto> lookupCustomerByPhone(
            @RequestParam @NotBlank String phone) {
        return bookingService.findLatestCustomerByPhone(phone)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Creates a confirmed booking from the admin panel without Stripe payment.
     *
     * @param request admin booking payload
     * @return created booking DTO
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponseDto> createAdminBooking(
            @RequestHeader(name = ADMIN_HOLD_SESSION_HEADER, required = false) String adminHoldSessionId,
            @Valid @RequestBody AdminBookingCreateRequestDto request) {
        return ResponseEntity.ok(bookingService.createAdminBooking(request, adminHoldSessionId));
    }

    /**
     * Creates a temporary admin-panel hold for phone booking before the form is
     * saved.
     *
     * @param adminHoldSessionId current admin hold session identifier
     * @param request            slot hold payload
     * @return pending hold DTO
     */
    @PostMapping(value = "/hold", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponseDto> holdAdminSlot(
            @RequestHeader(name = ADMIN_HOLD_SESSION_HEADER) @NotBlank String adminHoldSessionId,
            @Valid @RequestBody BookingHoldRequestDto request) {
        return ResponseEntity.ok(bookingService.holdAdminSlot(request, adminHoldSessionId));
    }

    /**
     * Extends an admin-panel hold while the booking form remains open.
     *
     * @param id                 held booking identifier
     * @param adminHoldSessionId current admin hold session identifier
     * @return refreshed hold DTO
     */
    @PostMapping("/{id}/hold-refresh")
    public ResponseEntity<BookingResponseDto> refreshAdminHold(
            @PathVariable @NotNull UUID id,
            @RequestHeader(name = ADMIN_HOLD_SESSION_HEADER) @NotBlank String adminHoldSessionId) {
        return ResponseEntity.ok(bookingService.refreshAdminHold(id, adminHoldSessionId));
    }

    /**
     * Releases an admin-panel hold.
     *
     * @param id                 held booking identifier
     * @param adminHoldSessionId current admin hold session identifier
     * @return no content
     */
    @DeleteMapping("/hold/{id}")
    public ResponseEntity<Void> releaseAdminHold(
            @PathVariable @NotNull UUID id,
            @RequestHeader(name = ADMIN_HOLD_SESSION_HEADER) @NotBlank String adminHoldSessionId) {
        bookingService.releaseAdminHold(id, adminHoldSessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates editable booking data from the admin panel.
     *
     * @param id      booking identifier
     * @param request updated booking payload
     * @return updated booking DTO
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponseDto> updateBooking(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody AdminBookingUpdateRequestDto request) {
        return ResponseEntity.ok(bookingService.updateBookingByAdmin(id, request));
    }

    /**
     * Cancels a booking from the admin panel.
     *
     * @param id booking identifier
     * @return updated booking DTO
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponseDto> cancelBooking(@PathVariable @NotNull UUID id) {
        return ResponseEntity.ok(bookingService.cancelBookingByAdmin(id));
    }

    /**
     * Returns active blacklist entries that cannot be used for bookings.
     *
     * @return blacklist entries
     */
    @GetMapping("/blacklist")
    public List<BookingBlacklistEntryResponseDto> getBlacklistEntries() {
        return bookingBlacklistService.getActiveEntries();
    }

    /**
     * Creates a new blacklist entry.
     *
     * @param request blacklist payload
     * @return created entry
     */
    @PostMapping(value = "/blacklist", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingBlacklistEntryResponseDto> createBlacklistEntry(
            @Valid @RequestBody BookingBlacklistEntryRequestDto request) {
        return ResponseEntity.ok(bookingBlacklistService.createEntry(request));
    }

    /**
     * Removes an active blacklist entry.
     *
     * @param id blacklist entry identifier
     * @return no content
     */
    @DeleteMapping("/blacklist/{id}")
    public ResponseEntity<Void> deleteBlacklistEntry(@PathVariable @NotNull UUID id) {
        bookingBlacklistService.deleteEntry(id);
        return ResponseEntity.noContent().build();
    }
}
