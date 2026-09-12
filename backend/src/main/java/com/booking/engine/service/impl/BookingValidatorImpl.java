package com.booking.engine.service.impl;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.payment.BookingPaymentConstants;
import com.booking.engine.validation.BookingValidationMessages;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link BookingValidator}.
 * Provides booking validator related business operations.
 */
@Service
@RequiredArgsConstructor
public class BookingValidatorImpl implements BookingValidator {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(BookingValidatorImpl.class);

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    // ---------------------- Services ----------------------

    private final BookingBlacklistService bookingBlacklistService;

    private final BookingStateMachine bookingStateMachine;

    private final BookingHoldAccessTokenService holdAccessTokenService;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicCustomerAllowed(String email, String phone) {
        if (bookingBlacklistService.isBlockedCustomer(email, phone)) {
            log.warn(
                    "event=public_booking_rejected reason=blacklisted_customer emailMask={} phoneHash={}",
                    maskEmailForLogs(email),
                    hashPhoneForLogs(phone));
            throw new BookingValidationException(BookingValidationMessages.PUBLIC_BOOKING_UNAVAILABLE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateHoldLimit(String clientIp, String clientDeviceId) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        long bookingIpHolds = clientIp != null
                ? bookingRepository.countActiveUnpaidHoldsByClientIp(clientIp, BookingStatus.PENDING, now)
                : 0L;
        long slotHoldIpCount = clientIp != null
                ? slotHoldRepository.countActiveByScopeAndHoldClientIp(SlotHoldScope.PUBLIC, clientIp, now)
                : 0L;
        if (clientIp != null && bookingIpHolds + slotHoldIpCount >= BookingHoldConstants.MAX_ACTIVE_HOLDS_PER_IP) {
            log.warn(
                    "event=booking_hold_limit_rejected reason=ip_limit_reached clientIpHash={} bookingHolds={} slotHolds={}",
                    hashValueForLogs(clientIp),
                    bookingIpHolds,
                    slotHoldIpCount);
            throw new BookingValidationException(BookingValidationMessages.HOLD_LIMIT_IP);
        }

        long bookingDeviceHolds = clientDeviceId != null
                ? bookingRepository.countActiveUnpaidHoldsByClientDeviceId(clientDeviceId, BookingStatus.PENDING, now)
                : 0L;
        long slotHoldDeviceCount = clientDeviceId != null
                ? slotHoldRepository.countActiveByScopeAndHoldClientDeviceId(
                        SlotHoldScope.PUBLIC,
                        clientDeviceId,
                        now)
                : 0L;
        if (clientDeviceId != null
                && bookingDeviceHolds + slotHoldDeviceCount >= BookingHoldConstants.MAX_ACTIVE_HOLDS_PER_DEVICE) {
            log.warn(
                    "event=booking_hold_limit_rejected reason=device_limit_reached clientDeviceHash={} bookingHolds={} slotHolds={}",
                    hashValueForLogs(clientDeviceId),
                    bookingDeviceHolds,
                    slotHoldDeviceCount);
            throw new BookingValidationException(BookingValidationMessages.HOLD_LIMIT_DEVICE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return;
        }

        if (!endTime.isAfter(startTime)) {
            throw new BookingValidationException(BookingValidationMessages.INVALID_TIME_RANGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateGlobalSlotHoldLimit(
            UUID employeeId,
            LocalDate bookingDate,
            LocalTime startTime,
            LocalTime endTime) {
        validateTimeRange(startTime, endTime);

        LocalDateTime now = LocalDateTime.now(getZoneId());
        long bookingSlotHolds = bookingRepository.countActiveUnpaidHoldsForSlot(
                employeeId,
                bookingDate,
                startTime,
                endTime,
                BookingStatus.PENDING,
                now);
        long slotHoldCount = slotHoldRepository.countActiveByScopeAndSlot(
                SlotHoldScope.PUBLIC,
                employeeId,
                bookingDate,
                startTime,
                endTime,
                now);

        int maxActivePerSlot = bookingProperties.getHoldLimits().getMaxActivePerSlot();
        if (bookingSlotHolds + slotHoldCount < maxActivePerSlot) {
            return;
        }

        log.warn(
                "event=booking_hold_limit_rejected reason=slot_limit_reached employeeId={} bookingDate={} startTime={} endTime={} bookingHolds={} slotHolds={} maxActivePerSlot={}",
                employeeId,
                bookingDate,
                startTime,
                endTime,
                bookingSlotHolds,
                slotHoldCount,
                maxActivePerSlot);
        throw new BookingValidationException(BookingValidationMessages.HOLD_LIMIT_SLOT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicSlotHoldOwnership(SlotHoldEntity slotHold, String holdAccessToken) {
        if (!holdAccessTokenService.matches(holdAccessToken, slotHold.getHoldAccessTokenHash())) {
            log.warn("event=public_hold_access_rejected resource=slot_hold slotHoldId={} reason=token_mismatch",
                    slotHold.getId());
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_UNAVAILABLE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicBookingOwnership(BookingEntity booking, String holdAccessToken) {
        if (!holdAccessTokenService.matches(holdAccessToken, booking.getHoldAccessTokenHash())) {
            log.warn("event=public_hold_access_rejected resource=booking bookingId={} reason=token_mismatch",
                    booking.getId());
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_UNAVAILABLE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePendingBookingAvailability(BookingEntity booking) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_ALREADY_CONFIRMED);
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_ALREADY_CANCELLED);
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_EXPIRED);
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_ALREADY_COMPLETED);
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_UNAVAILABLE);
        }

        if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(now)) {
            bookingStateMachine.markBookingExpired(booking);
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_EXPIRED);
        }

        validateHeldBookingSlotStillAvailable(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicSlotHoldAvailability(SlotHoldEntity slotHold) {
        if (slotHold.getHoldScope() != SlotHoldScope.PUBLIC) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_UNAVAILABLE);
        }

        validateActiveSlotHold(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicSlotHoldCancellation(SlotHoldEntity slotHold) {
        if (slotHold.getHoldScope() != SlotHoldScope.PUBLIC) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_UNAVAILABLE);
        }

        if (BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(slotHold.getStripePaymentStatus())
                || slotHold.getPaymentCapturedAt() != null) {
            throw new BookingValidationException(BookingValidationMessages.PAID_BOOKINGS_ADMIN_ONLY);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateBookingCanFinalizePayment(BookingEntity booking) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return;
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_ALREADY_CANCELLED);
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_EXPIRED);
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_ALREADY_COMPLETED);
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_UNAVAILABLE);
        }

        if (bookingStateMachine.isPaidPendingBooking(booking)) {
            return;
        }

        if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(now)) {
            bookingStateMachine.markBookingExpired(booking);
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_EXPIRED);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSlotHoldCanFinalizePayment(SlotHoldEntity slotHold) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (!Boolean.TRUE.equals(slotHold.getActive())) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_UNAVAILABLE);
        }

        if (slotHold.getExpiresAt() == null || !slotHold.getExpiresAt().isAfter(now)) {
            bookingStateMachine.releaseSlotHold(slotHold);
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_EXPIRED);
        }

        validateSlotHoldStillAvailable(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateBookingCanAcceptSuccessfulPayment(BookingEntity booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_ALREADY_CANCELLED);
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_ALREADY_COMPLETED);
        }

        validateHeldBookingSlotStillAvailable(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSlotHoldCanAcceptSuccessfulPayment(SlotHoldEntity slotHold) {
        if (!Boolean.TRUE.equals(slotHold.getActive())) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_UNAVAILABLE);
        }

        validateSlotHoldStillAvailable(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicCancellation(BookingEntity booking, UUID id) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.warn("event=booking_cancel_rejected reason=already_cancelled bookingId={}", id);
            throw new IllegalStateException("Booking is already canceled");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("This booking can no longer be changed.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingValidationException("Only pending booking holds can be cancelled from the public site.");
        }

        if (booking.getPaymentCapturedAt() != null
                || BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(booking.getStripePaymentStatus())) {
            throw new BookingValidationException(BookingValidationMessages.PAID_BOOKINGS_ADMIN_ONLY);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminCancellation(BookingEntity booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException("This booking has already been cancelled.");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingValidationException("This booking has already expired.");
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("Completed bookings cannot be cancelled.");
        }

        if (booking.getStatus() == BookingStatus.PENDING && !bookingStateMachine.isPaidPendingBooking(booking)) {
            validatePendingBookingAvailability(booking);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminUpdateFinancialFields(BookingEntity booking, AdminBookingUpdateRequestDto request) {
        if (!bookingStateMachine.hasCapturedPayment(booking)) {
            return;
        }

        if (!amountsEqual(booking.getHoldAmount(), request.getHoldAmount())) {
            throw new BookingValidationException(BookingValidationMessages.PAID_BOOKING_AMOUNT_IMMUTABLE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminHoldSessionId(String adminHoldSessionId) {
        if (adminHoldSessionId == null || adminHoldSessionId.trim().isBlank()) {
            throw new BookingValidationException(BookingValidationMessages.ADMIN_HOLD_INVALID);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminHoldOwnership(BookingEntity booking, String adminHoldSessionId) {
        if (!bookingStateMachine.matchesAdminHoldSession(booking, adminHoldSessionId)) {
            throw new BookingValidationException(BookingValidationMessages.ADMIN_HOLD_INVALID);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminSlotHoldOwnership(SlotHoldEntity slotHold, String adminHoldSessionId) {
        if (!bookingStateMachine.matchesAdminHoldSession(slotHold, adminHoldSessionId)) {
            throw new BookingValidationException(BookingValidationMessages.ADMIN_HOLD_INVALID);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminHoldAvailability(BookingEntity booking) {
        if (!bookingStateMachine.isAdminPanelHold(booking) || bookingStateMachine.isPaidPendingBooking(booking)) {
            throw new BookingValidationException(BookingValidationMessages.ADMIN_HOLD_INVALID);
        }

        try {
            validatePendingBookingAvailability(booking);
        } catch (BookingValidationException exception) {
            throw new BookingValidationException(BookingValidationMessages.ADMIN_HOLD_INVALID);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminSlotHoldAvailability(SlotHoldEntity slotHold) {
        if (!bookingStateMachine.isAdminPanelHold(slotHold)) {
            throw new BookingValidationException(BookingValidationMessages.ADMIN_HOLD_INVALID);
        }

        try {
            validateActiveSlotHold(slotHold);
        } catch (BookingValidationException exception) {
            throw new BookingValidationException(BookingValidationMessages.ADMIN_HOLD_INVALID);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean slotDefinitionChanged(BookingEntity booking, AdminBookingUpdateRequestDto request) {
        return !booking.getEmployee().getId().equals(request.getEmployeeId())
                || !booking.getTreatment().getId().equals(request.getTreatmentId())
                || !booking.getBookingDate().equals(request.getBookingDate())
                || !booking.getStartTime().equals(request.getStartTime())
                || !booking.getEndTime().equals(request.getEndTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldValidateAdminBookingSlot(BookingStatus status) {
        return status == BookingStatus.CONFIRMED
                || status == BookingStatus.DONE
                || status == BookingStatus.CANCELLED
                || status == BookingStatus.PENDING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matchesAdminHeldSlot(BookingEntity booking, AdminBookingCreateRequestDto request) {
        return booking.getEmployee().getId().equals(request.getEmployeeId())
                && booking.getTreatment().getId().equals(request.getTreatmentId())
                && booking.getBookingDate().equals(request.getBookingDate())
                && booking.getStartTime().equals(request.getStartTime())
                && booking.getEndTime().equals(request.getEndTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matchesAdminHeldSlot(SlotHoldEntity slotHold, AdminBookingCreateRequestDto request) {
        return slotHold.getEmployee().getId().equals(request.getEmployeeId())
                && slotHold.getTreatment().getId().equals(request.getTreatmentId())
                && slotHold.getBookingDate().equals(request.getBookingDate())
                && slotHold.getStartTime().equals(request.getStartTime())
                && slotHold.getEndTime().equals(request.getEndTime());
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Validates that the slot hold is still active and not expired.
     */
    private void validateActiveSlotHold(SlotHoldEntity slotHold) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (!Boolean.TRUE.equals(slotHold.getActive())) {
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_UNAVAILABLE);
        }

        if (slotHold.getExpiresAt() == null || !slotHold.getExpiresAt().isAfter(now)) {
            bookingStateMachine.releaseSlotHold(slotHold);
            throw new BookingValidationException(BookingValidationMessages.APPOINTMENT_HOLD_EXPIRED);
        }

        validateSlotHoldStillAvailable(slotHold);
    }

    /*
     * Validates that the held booking slot is still available before continuing.
     */
    private void validateHeldBookingSlotStillAvailable(BookingEntity booking) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        List<BookingEntity> sameDayBookings = Optional.ofNullable(
                bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                        booking.getEmployee().getId(),
                        booking.getBookingDate(),
                        List.of(
                                BookingStatus.PENDING,
                                BookingStatus.CONFIRMED,
                                BookingStatus.CANCELLED,
                                BookingStatus.DONE)))
                .orElse(List.of());

        BookingEntity conflictingBooking = sameDayBookings.stream()
                .filter(existing -> booking.getId() == null || !booking.getId().equals(existing.getId()))
                .filter(existing -> booking.getStartTime().isBefore(existing.getEndTime())
                        && booking.getEndTime().isAfter(existing.getStartTime()))
                .filter(existing -> existing.getStatus() == BookingStatus.CONFIRMED
                        || existing.getStatus() == BookingStatus.DONE
                        || bookingStateMachine.isBlockingPendingSlot(existing, now)
                        || bookingStateMachine.isLockedCancelledSlot(existing))
                .findFirst()
                .orElse(null);

        if (conflictingBooking == null) {
            return;
        }

        if (bookingStateMachine.isActiveHoldSlot(conflictingBooking, now)) {
            throw new BookingValidationException(BookingValidationMessages.SLOT_HELD_BY_ANOTHER_GUEST);
        }

        throw new BookingValidationException(BookingValidationMessages.SLOT_ALREADY_BOOKED_BY_SOMEONE_ELSE);
    }

    /*
     * Validates that the slot hold still points to an available time slot.
     */
    private void validateSlotHoldStillAvailable(SlotHoldEntity slotHold) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        List<BookingEntity> sameDayBookings = Optional.ofNullable(
                bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                        slotHold.getEmployee().getId(),
                        slotHold.getBookingDate(),
                        List.of(
                                BookingStatus.PENDING,
                                BookingStatus.CONFIRMED,
                                BookingStatus.CANCELLED,
                                BookingStatus.DONE)))
                .orElse(List.of());

        BookingEntity conflictingBooking = sameDayBookings.stream()
                .filter(existing -> slotHold.getStartTime().isBefore(existing.getEndTime())
                        && slotHold.getEndTime().isAfter(existing.getStartTime()))
                .filter(existing -> existing.getStatus() == BookingStatus.CONFIRMED
                        || existing.getStatus() == BookingStatus.DONE
                        || bookingStateMachine.isBlockingPendingSlot(existing, now)
                        || bookingStateMachine.isLockedCancelledSlot(existing))
                .findFirst()
                .orElse(null);

        if (conflictingBooking != null) {
            if (bookingStateMachine.isActiveHoldSlot(conflictingBooking, now)) {
                throw new BookingValidationException(BookingValidationMessages.SLOT_HELD_BY_ANOTHER_GUEST);
            }

            throw new BookingValidationException(BookingValidationMessages.SLOT_ALREADY_BOOKED_BY_SOMEONE_ELSE);
        }

        SlotHoldEntity conflictingSlotHold = Optional.ofNullable(
                slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                        slotHold.getEmployee().getId(),
                        slotHold.getBookingDate(),
                        now))
                .orElse(List.of())
                .stream()
                .filter(existing -> !slotHold.getId().equals(existing.getId()))
                .filter(existing -> slotHold.getStartTime().isBefore(existing.getEndTime())
                        && slotHold.getEndTime().isAfter(existing.getStartTime()))
                .findFirst()
                .orElse(null);

        if (conflictingSlotHold != null) {
            throw new BookingValidationException(BookingValidationMessages.SLOT_HELD_BY_ANOTHER_GUEST);
        }
    }

    /*
     * Compares currency amounts by value so equivalent database/request scales
     * such as 10 and 10.00 do not count as mutations.
     */
    private boolean amountsEqual(BigDecimal currentAmount, BigDecimal requestedAmount) {
        if (currentAmount == null || requestedAmount == null) {
            return currentAmount == requestedAmount;
        }

        return currentAmount.compareTo(requestedAmount) == 0;
    }

    /*
     * Resolves the configured booking timezone as a {@link ZoneId}.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }

    /*
     * Hashes a free-form value before writing it to operational logs.
     */
    private String hashValueForLogs(String value) {
        return SensitiveLogSanitizer.hashValue(value);
    }

    /*
     * Masks customer email before writing it to operational logs.
     */
    private String maskEmailForLogs(String email) {
        return SensitiveLogSanitizer.maskEmail(email);
    }

    /*
     * Hashes customer phone before writing it to operational logs.
     */
    private String hashPhoneForLogs(String phone) {
        return SensitiveLogSanitizer.hashPhone(phone);
    }
}
