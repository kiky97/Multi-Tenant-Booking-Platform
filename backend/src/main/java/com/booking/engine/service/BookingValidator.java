package com.booking.engine.service;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Service contract for booking validator operations.
 * Defines booking validator related business operations.
 */
public interface BookingValidator {

    /**
     * Rejects public booking customers that match the blacklist.
     *
     * @param email customer email, may be null
     * @param phone customer phone, may be null
     */
    void validatePublicCustomerAllowed(String email, String phone);

    /**
     * Enforces active public hold limits for a client.
     *
     * @param clientIp       client IP address
     * @param clientDeviceId client device identifier
     */
    void validateHoldLimit(String clientIp, String clientDeviceId);

    /**
     * Ensures a booking time range has positive duration.
     *
     * @param startTime slot start time
     * @param endTime   slot end time
     */
    void validateTimeRange(LocalTime startTime, LocalTime endTime);

    /**
     * Enforces global active public hold limits for a slot.
     *
     * @param employeeId  employee identifier
     * @param bookingDate booking date
     * @param startTime   slot start time
     * @param endTime     slot end time
     */
    void validateGlobalSlotHoldLimit(
            UUID employeeId,
            LocalDate bookingDate,
            LocalTime startTime,
            LocalTime endTime);

    /**
     * Ensures the public caller owns the slot hold.
     *
     * @param slotHold        slot-hold entity
     * @param holdAccessToken raw access token supplied by caller
     */
    void validatePublicSlotHoldOwnership(SlotHoldEntity slotHold, String holdAccessToken);

    /**
     * Ensures the public caller owns the booking hold.
     *
     * @param booking         booking entity
     * @param holdAccessToken raw access token supplied by caller
     */
    void validatePublicBookingOwnership(BookingEntity booking, String holdAccessToken);

    /**
     * Ensures a legacy pending booking can still proceed to checkout.
     *
     * @param booking booking entity
     */
    void validatePendingBookingAvailability(BookingEntity booking);

    /**
     * Ensures a temporary slot hold can still proceed to public checkout.
     *
     * @param slotHold slot-hold entity
     */
    void validatePublicSlotHoldAvailability(SlotHoldEntity slotHold);

    /**
     * Ensures a temporary slot hold may be cancelled by the public flow.
     *
     * @param slotHold slot-hold entity
     */
    void validatePublicSlotHoldCancellation(SlotHoldEntity slotHold);

    /**
     * Ensures a booking can be finalized after payment.
     *
     * @param booking booking entity
     */
    void validateBookingCanFinalizePayment(BookingEntity booking);

    /**
     * Ensures a slot hold can be finalized after payment.
     *
     * @param slotHold slot-hold entity
     */
    void validateSlotHoldCanFinalizePayment(SlotHoldEntity slotHold);

    /**
     * Ensures a booking can accept a successful Stripe payment update.
     *
     * @param booking booking entity
     */
    void validateBookingCanAcceptSuccessfulPayment(BookingEntity booking);

    /**
     * Ensures a slot hold can accept a successful Stripe payment update.
     *
     * @param slotHold slot-hold entity
     */
    void validateSlotHoldCanAcceptSuccessfulPayment(SlotHoldEntity slotHold);

    /**
     * Ensures public cancellation is allowed for the booking.
     *
     * @param booking booking entity
     * @param id      booking identifier
     */
    void validatePublicCancellation(BookingEntity booking, UUID id);

    /**
     * Ensures admin cancellation is allowed for the booking.
     *
     * @param booking booking entity
     */
    void validateAdminCancellation(BookingEntity booking);

    /**
     * Ensures admin edits do not change captured payment amounts.
     *
     * @param booking booking entity
     * @param request admin update payload
     */
    void validateAdminUpdateFinancialFields(BookingEntity booking, AdminBookingUpdateRequestDto request);

    /**
     * Ensures an admin hold session id is present.
     *
     * @param adminHoldSessionId admin hold session identifier
     */
    void validateAdminHoldSessionId(String adminHoldSessionId);

    /**
     * Ensures a booking hold belongs to the admin hold session.
     *
     * @param booking            booking entity
     * @param adminHoldSessionId admin hold session identifier
     */
    void validateAdminHoldOwnership(BookingEntity booking, String adminHoldSessionId);

    /**
     * Ensures a temporary slot hold belongs to the admin hold session.
     *
     * @param slotHold           slot-hold entity
     * @param adminHoldSessionId admin hold session identifier
     */
    void validateAdminSlotHoldOwnership(SlotHoldEntity slotHold, String adminHoldSessionId);

    /**
     * Ensures an admin booking hold is still active and usable.
     *
     * @param booking booking entity
     */
    void validateAdminHoldAvailability(BookingEntity booking);

    /**
     * Ensures an admin slot hold is still active and usable.
     *
     * @param slotHold slot-hold entity
     */
    void validateAdminSlotHoldAvailability(SlotHoldEntity slotHold);

    /**
     * Detects whether admin edits changed the slot-defining fields.
     *
     * @param booking booking entity
     * @param request admin update payload
     * @return true when employee, treatment, date, or time changed
     */
    boolean slotDefinitionChanged(BookingEntity booking, AdminBookingUpdateRequestDto request);

    /**
     * Determines whether the requested status should reserve a public slot.
     *
     * @param status requested booking status
     * @return true when availability validation should run
     */
    boolean shouldValidateAdminBookingSlot(BookingStatus status);

    /**
     * Checks whether an admin-held booking still matches the submitted slot.
     *
     * @param booking booking entity
     * @param request admin create payload
     * @return true when the held booking matches the requested slot
     */
    boolean matchesAdminHeldSlot(BookingEntity booking, AdminBookingCreateRequestDto request);

    /**
     * Checks whether an admin-held slot hold still matches the submitted slot.
     *
     * @param slotHold slot-hold entity
     * @param request  admin create payload
     * @return true when the held slot hold matches the requested slot
     */
    boolean matchesAdminHeldSlot(SlotHoldEntity slotHold, AdminBookingCreateRequestDto request);
}
