package com.booking.engine.service;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingEntity;
import java.util.UUID;

/**
 * Service contract for admin booking hold lifecycle operations.
 */
public interface AdminBookingHoldService {

    /**
     * Temporarily reserves a slot for the current admin booking session.
     *
     * @param request            request payload
     * @param adminHoldSessionId admin hold session identifier
     * @return temporary hold details
     */
    BookingResponseDto holdAdminSlot(BookingHoldRequestDto request, String adminHoldSessionId);

    /**
     * Extends an active admin hold owned by the current admin session.
     *
     * @param id                 booking or hold identifier
     * @param adminHoldSessionId admin hold session identifier
     * @return refreshed hold details
     */
    BookingResponseDto refreshAdminHold(UUID id, String adminHoldSessionId);

    /**
     * Releases an active admin hold owned by the current admin session.
     *
     * @param id                 booking or hold identifier
     * @param adminHoldSessionId admin hold session identifier
     */
    void releaseAdminHold(UUID id, String adminHoldSessionId);

    /**
     * Converts a held admin slot into a confirmed admin booking.
     *
     * @param request            request payload
     * @param adminHoldSessionId admin hold session identifier
     * @param customerEmail      normalized customer email
     * @return confirmed booking entity
     */
    BookingEntity confirmAdminHeldBooking(
            AdminBookingCreateRequestDto request,
            String adminHoldSessionId,
            String customerEmail);
}
