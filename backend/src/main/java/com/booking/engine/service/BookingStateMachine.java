package com.booking.engine.service;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.TreatmentEntity;
import java.time.LocalDateTime;

/**
 * Service contract for booking state machine operations.
 * Defines booking state machine related business operations.
 */
public interface BookingStateMachine {

    String ADMIN_HOLD_CLIENT_IP = "admin-panel";
    String ADMIN_HOLD_DEVICE_PREFIX = "admin-panel:";

    /**
     * Applies the canonical state transition for a successful Stripe payment.
     *
     * @param booking       booking entity
     * @param paymentStatus Stripe PaymentIntent status
     * @param source        short source label for diagnostics
     */
    void applySuccessfulStripePaymentState(BookingEntity booking, String paymentStatus, String source);

    /**
     * Applies the canonical state transition for a failed booking payment.
     *
     * @param booking       booking entity
     * @param paymentStatus Stripe PaymentIntent status
     */
    void applyFailedStripePaymentState(BookingEntity booking, String paymentStatus);

    /**
     * Applies the canonical state transition for a failed slot-hold payment.
     *
     * @param slotHold      slot-hold entity
     * @param paymentStatus Stripe PaymentIntent status
     */
    void applyFailedStripePaymentState(SlotHoldEntity slotHold, String paymentStatus);

    /**
     * Applies public cancellation rules to a booking.
     *
     * @param booking booking entity
     */
    void cancelPublicBooking(BookingEntity booking);

    /**
     * Applies admin cancellation rules to a booking.
     *
     * @param booking booking entity
     */
    void cancelByAdmin(BookingEntity booking);

    /**
     * Applies status-related state changes from admin booking edits.
     *
     * @param booking booking entity
     * @param status  requested booking status
     */
    void applyAdminUpdateState(BookingEntity booking, BookingStatus status);

    /**
     * Applies financial fields from admin booking edits.
     *
     * @param booking booking entity
     * @param request admin update payload
     */
    void applyAdminUpdateFinancialFields(BookingEntity booking, AdminBookingUpdateRequestDto request);

    /**
     * Marks a pending booking hold as expired.
     *
     * @param booking booking entity
     * @return updated booking entity
     */
    BookingEntity markBookingExpired(BookingEntity booking);

    /**
     * Releases an unpaid admin-panel booking hold.
     *
     * @param booking booking entity
     */
    void releaseAdminHold(BookingEntity booking);

    /**
     * Releases a temporary slot hold.
     *
     * @param slotHold slot-hold entity
     */
    void releaseSlotHold(SlotHoldEntity slotHold);

    /**
     * Finalizes a paid temporary slot hold into a confirmed booking.
     *
     * @param slotHold      slot-hold entity
     * @param paymentStatus Stripe PaymentIntent status
     * @param source        short source label for diagnostics
     * @return confirmed booking entity
     */
    BookingEntity finalizePaidSlotHold(SlotHoldEntity slotHold, String paymentStatus, String source);

    /**
     * Copies final admin-confirmed booking details onto the booking entity.
     *
     * @param booking       booking entity
     * @param request       request payload
     * @param employee      employee entity
     * @param treatment     treatment entity
     * @param customerEmail normalized customer email
     */
    void applyConfirmedAdminBookingDetails(
            BookingEntity booking,
            AdminBookingCreateRequestDto request,
            EmployeeEntity employee,
            TreatmentEntity treatment,
            String customerEmail);

    /**
     * Detects bookings whose payment amount is already captured.
     *
     * @param booking booking entity
     * @return true when payment capture timestamp is present or Stripe status succeeded
     */
    boolean hasCapturedPayment(BookingEntity booking);

    /**
     * Detects pending bookings whose Stripe payment is already captured.
     *
     * @param booking booking entity
     * @return true when the pending booking is already paid
     */
    boolean isPaidPendingBooking(BookingEntity booking);

    /**
     * Detects pending bookings that should still block slot availability.
     *
     * @param booking booking entity
     * @param now     current timestamp
     * @return true when the booking blocks the slot
     */
    boolean isBlockingPendingSlot(BookingEntity booking, LocalDateTime now);

    /**
     * Detects unpaid holds that are still active.
     *
     * @param booking booking entity
     * @param now     current timestamp
     * @return true when the hold is still active
     */
    boolean isActiveHoldSlot(BookingEntity booking, LocalDateTime now);

    /**
     * Detects cancelled bookings that intentionally keep their slot locked.
     *
     * @param booking booking entity
     * @return true when the cancelled booking locks the slot
     */
    boolean isLockedCancelledSlot(BookingEntity booking);

    /**
     * Checks whether a booking hold belongs to an admin hold session.
     *
     * @param booking            booking entity
     * @param adminHoldSessionId admin hold session identifier
     * @return true when the session owns the booking hold
     */
    boolean matchesAdminHoldSession(BookingEntity booking, String adminHoldSessionId);

    /**
     * Checks whether a slot hold belongs to an admin hold session.
     *
     * @param slotHold           slot-hold entity
     * @param adminHoldSessionId admin hold session identifier
     * @return true when the session owns the slot hold
     */
    boolean matchesAdminHoldSession(SlotHoldEntity slotHold, String adminHoldSessionId);

    /**
     * Detects booking holds created by the admin panel.
     *
     * @param booking booking entity
     * @return true when the booking is an admin-panel hold
     */
    boolean isAdminPanelHold(BookingEntity booking);

    /**
     * Detects temporary slot holds created by the admin panel.
     *
     * @param slotHold slot-hold entity
     * @return true when the slot hold is an admin-panel hold
     */
    boolean isAdminPanelHold(SlotHoldEntity slotHold);

    /**
     * Converts an admin hold session id into the persisted hold-device value.
     *
     * @param adminHoldSessionId admin hold session identifier
     * @return hold-device value
     */
    String toAdminHoldDeviceId(String adminHoldSessionId);
}
