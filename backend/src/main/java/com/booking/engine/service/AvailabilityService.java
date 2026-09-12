package com.booking.engine.service;

import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.AvailabilitySlotDto;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for availability operations.
 * Defines availability related business operations.
 */
public interface AvailabilityService {

    /**
     * Validates all booking constraints including:
     * - Employee and treatment existence and active status
     * - Booking date/time not in the past
     * - Working hours compliance
     * - Slot availability (no conflicts)
     *
     * @param request the booking request to validate
     * @throws com.booking.engine.exception.BookingValidationException if validation
     *                                                                 fails
     */
    void validateBookingRequest(BookingRequestDto request);

    /**
     * Validates a selected booking slot without requiring customer or payment
     * details. Used by the temporary hold flow before payment is collected.
     *
     * @param employeeId  employee identifier
     * @param treatmentId treatment identifier
     * @param bookingDate selected booking date
     * @param startTime   selected slot start time
     * @param endTime     selected slot end time
     */
    void validateSlotSelection(
            UUID employeeId,
            UUID treatmentId,
            LocalDate bookingDate,
            LocalTime startTime,
            LocalTime endTime);

    /**
     * Validates a selected booking slot while ignoring the provided booking
     * identifier. Used by admin edit flows so an existing appointment can keep
     * its own slot without being treated as a self-conflict.
     *
     * @param employeeId       employee identifier
     * @param treatmentId      treatment identifier
     * @param bookingDate      selected booking date
     * @param startTime        selected slot start time
     * @param endTime          selected slot end time
     * @param ignoredBookingId booking identifier to exclude from conflict checks
     */
    void validateSlotSelectionExcludingBooking(
            UUID employeeId,
            UUID treatmentId,
            LocalDate bookingDate,
            LocalTime startTime,
            LocalTime endTime,
            UUID ignoredBookingId);

    /**
     * Calculates fixed one-hour availability slots for a employee on a specific
     * date.
     *
     * @param employeeId  employee identifier
     * @param date        target date
     * @param treatmentId treatment identifier
     * @return list of slots with availability flags
     */
    List<AvailabilitySlotDto> getAvailability(UUID employeeId, LocalDate date, UUID treatmentId);
}
