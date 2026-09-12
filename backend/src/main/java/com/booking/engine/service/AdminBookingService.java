package com.booking.engine.service;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service contract for admin booking operations.
 * Defines admin booking related business operations.
 */
public interface AdminBookingService {

    /**
     * Creates a confirmed booking from the admin booking form.
     *
     * @return created booking details
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto createAdminBooking(AdminBookingCreateRequestDto request);

    /**
     * Creates a confirmed admin booking, optionally from an admin-held slot.
     *
     * @param request            request payload
     * @param adminHoldSessionId admin hold session identifier
     * @return created booking details
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto createAdminBooking(
            AdminBookingCreateRequestDto request,
            String adminHoldSessionId);

    /**
     * Temporarily reserves a slot for the current admin booking session.
     *
     * @param request            request payload
     * @param adminHoldSessionId admin hold session identifier
     * @return temporary hold details
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto holdAdminSlot(BookingHoldRequestDto request, String adminHoldSessionId);

    /**
     * Extends an active admin hold owned by the current admin session.
     *
     * @param id                 booking or hold identifier
     * @param adminHoldSessionId admin hold session identifier
     * @return refreshed hold details
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto refreshAdminHold(UUID id, String adminHoldSessionId);

    /**
     * Releases an active admin hold owned by the current admin session.
     *
     * @param id                 booking or hold identifier
     * @param adminHoldSessionId admin hold session identifier
     */
    @PreAuthorize("hasRole('ADMIN')")
    void releaseAdminHold(UUID id, String adminHoldSessionId);

    /**
     * Retrieves active bookings for admin review, optionally filtered by search.
     *
     * @param search search text
     * @return list response with counts
     */
    @PreAuthorize("hasRole('ADMIN')")
    AdminBookingListResponseDto getAdminBookings(String search);

    /**
     * Finds the latest customer contact snapshot matching a phone number.
     *
     * @param phone phone value
     * @return latest matching customer details, when present
     */
    @PreAuthorize("hasRole('ADMIN')")
    Optional<AdminBookingCustomerLookupResponseDto> findLatestCustomerByPhone(String phone);

    /**
     * Updates booking details from the admin booking form.
     *
     * @param id      booking identifier
     * @param request request payload
     * @return updated booking details
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto updateBookingByAdmin(UUID id, AdminBookingUpdateRequestDto request);

    /**
     * Cancels a booking from the admin review flow.
     *
     * @param id booking identifier
     * @return cancelled booking details
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto cancelBookingByAdmin(UUID id);
}
