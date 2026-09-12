package com.booking.engine.service.impl;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.payment.BookingPaymentConstants;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link BookingStateMachine}.
 * Provides booking state machine related business operations.
 */
@Service
@RequiredArgsConstructor
public class BookingStateMachineImpl implements BookingStateMachine {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(BookingStateMachineImpl.class);

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void applySuccessfulStripePaymentState(BookingEntity booking, String paymentStatus, String source) {
        booking.setStripePaymentStatus(paymentStatus);
        booking.setPaymentCapturedAt(resolveCapturedAt(booking));
        booking.setPaymentReleasedAt(null);
        booking.setExpiresAt(null);
        booking.setSlotLocked(false);

        if (booking.getStatus() == BookingStatus.PENDING
                || booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.EXPIRED) {
            booking.setStatus(BookingStatus.CONFIRMED);
            return;
        }

        log.warn(
                "event=payment_success_ignored reason=invalid_booking_status source={} bookingId={} currentStatus={}",
                source,
                booking.getId(),
                booking.getStatus());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyFailedStripePaymentState(BookingEntity booking, String paymentStatus) {
        booking.setStripePaymentStatus(paymentStatus);
        booking.setPaymentReleasedAt(LocalDateTime.now(getZoneId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyFailedStripePaymentState(SlotHoldEntity slotHold, String paymentStatus) {
        slotHold.setStripePaymentStatus(paymentStatus);
        slotHold.setPaymentReleasedAt(LocalDateTime.now(getZoneId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelPublicBooking(BookingEntity booking) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);
        booking.setSlotLocked(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelByAdmin(BookingEntity booking) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);
        booking.setSlotLocked(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyAdminUpdateState(BookingEntity booking, BookingStatus status) {
        booking.setStatus(status);
        booking.setSlotLocked(status == BookingStatus.CANCELLED);

        if (status != BookingStatus.PENDING) {
            booking.setExpiresAt(null);
        }

        if (status == BookingStatus.CONFIRMED) {
            booking.setPaymentReleasedAt(null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyAdminUpdateFinancialFields(BookingEntity booking, AdminBookingUpdateRequestDto request) {
        if (hasCapturedPayment(booking)) {
            return;
        }

        booking.setHoldAmount(request.getHoldAmount());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BookingEntity markBookingExpired(BookingEntity booking) {
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setExpiresAt(null);
        return bookingRepository.save(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void releaseAdminHold(BookingEntity booking) {
        if (!isAdminPanelHold(booking)
                || booking.getStatus() != BookingStatus.PENDING
                || isPaidPendingBooking(booking)) {
            return;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);
        booking.setPaymentReleasedAt(LocalDateTime.now(getZoneId()));
        booking.setSlotLocked(false);
        booking.setActive(false);
        booking.setHoldClientIp(null);
        booking.setHoldClientDeviceId(null);
        booking.setHoldAccessTokenHash(null);
        bookingRepository.save(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void releaseSlotHold(SlotHoldEntity slotHold) {
        slotHoldRepository.delete(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BookingEntity finalizePaidSlotHold(SlotHoldEntity slotHold, String paymentStatus, String source) {
        BookingEntity booking = new BookingEntity();
        booking.setActive(true);
        booking.setEmployee(slotHold.getEmployee());
        booking.setTreatment(slotHold.getTreatment());
        booking.setCustomerName(slotHold.getCustomerName());
        booking.setCustomerEmail(slotHold.getCustomerEmail());
        booking.setCustomerPhone(slotHold.getCustomerPhone());
        booking.setBookingDate(slotHold.getBookingDate());
        booking.setStartTime(slotHold.getStartTime());
        booking.setEndTime(slotHold.getEndTime());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        booking.setStripePaymentIntentId(slotHold.getStripePaymentIntentId());
        booking.setStripePaymentStatus(paymentStatus);
        booking.setHoldAmount(slotHold.getHoldAmount());
        booking.setHoldClientIp(slotHold.getHoldClientIp());
        booking.setHoldClientDeviceId(slotHold.getHoldClientDeviceId());
        booking.setHoldAccessTokenHash(slotHold.getHoldAccessTokenHash());
        booking.setPaymentCapturedAt(resolveCapturedAt(slotHold));
        booking.setPaymentReleasedAt(null);
        booking.setSlotLocked(false);

        BookingEntity savedBooking = bookingRepository.save(booking);
        releaseSlotHold(slotHold);

        log.info("event=slot_hold_finalized source={} bookingId={} paymentIntentHash={}",
                source,
                savedBooking.getId(),
                hashPaymentIntentForLogs(savedBooking.getStripePaymentIntentId()));
        return savedBooking;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyConfirmedAdminBookingDetails(
            BookingEntity booking,
            AdminBookingCreateRequestDto request,
            EmployeeEntity employee,
            TreatmentEntity treatment,
            String customerEmail) {
        booking.setActive(true);
        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setCustomerName(request.getCustomerName().trim());
        booking.setCustomerEmail(customerEmail);
        booking.setCustomerPhone(request.getCustomerPhone().trim());
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        booking.setHoldAmount(treatment.getPrice());
        booking.setStripePaymentIntentId(null);
        booking.setStripePaymentStatus(null);
        booking.setPaymentCapturedAt(null);
        booking.setPaymentReleasedAt(null);
        booking.setSlotLocked(false);
        booking.setHoldClientIp(null);
        booking.setHoldClientDeviceId(null);
        booking.setHoldAccessTokenHash(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasCapturedPayment(BookingEntity booking) {
        return booking.getPaymentCapturedAt() != null
                || BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(booking.getStripePaymentStatus());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPaidPendingBooking(BookingEntity booking) {
        return booking.getStatus() == BookingStatus.PENDING && hasCapturedPayment(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBlockingPendingSlot(BookingEntity booking, LocalDateTime now) {
        return booking.getStatus() == BookingStatus.PENDING
                && (isPaidPendingBooking(booking) || isActiveHoldSlot(booking, now));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActiveHoldSlot(BookingEntity booking, LocalDateTime now) {
        return booking.getStatus() == BookingStatus.PENDING
                && !isPaidPendingBooking(booking)
                && booking.getExpiresAt() != null
                && booking.getExpiresAt().isAfter(now);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLockedCancelledSlot(BookingEntity booking) {
        return booking.getStatus() == BookingStatus.CANCELLED
                && Boolean.TRUE.equals(booking.getSlotLocked());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matchesAdminHoldSession(BookingEntity booking, String adminHoldSessionId) {
        return toAdminHoldDeviceId(adminHoldSessionId).equals(booking.getHoldClientDeviceId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matchesAdminHoldSession(SlotHoldEntity slotHold, String adminHoldSessionId) {
        return toAdminHoldDeviceId(adminHoldSessionId).equals(slotHold.getHoldClientDeviceId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAdminPanelHold(BookingEntity booking) {
        return booking.getHoldClientDeviceId() != null
                && booking.getHoldClientDeviceId().startsWith(ADMIN_HOLD_DEVICE_PREFIX);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAdminPanelHold(SlotHoldEntity slotHold) {
        return slotHold.getHoldScope() == SlotHoldScope.ADMIN
                && slotHold.getHoldClientDeviceId() != null
                && slotHold.getHoldClientDeviceId().startsWith(ADMIN_HOLD_DEVICE_PREFIX);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toAdminHoldDeviceId(String adminHoldSessionId) {
        return ADMIN_HOLD_DEVICE_PREFIX + adminHoldSessionId.trim();
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Resolves the timestamp that should be treated as payment capture time.
     */
    private LocalDateTime resolveCapturedAt(BookingEntity booking) {
        return booking.getPaymentCapturedAt() != null
                ? booking.getPaymentCapturedAt()
                : LocalDateTime.now(getZoneId());
    }

    /*
     * Resolves the timestamp that should be treated as payment capture time.
     */
    private LocalDateTime resolveCapturedAt(SlotHoldEntity slotHold) {
        return slotHold.getPaymentCapturedAt() != null
                ? slotHold.getPaymentCapturedAt()
                : LocalDateTime.now(getZoneId());
    }

    /*
     * Resolves the configured booking timezone as a {@link ZoneId}.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }

    /*
     * Hashes a Stripe PaymentIntent identifier for operational logs.
     */
    private String hashPaymentIntentForLogs(String paymentIntentId) {
        return SensitiveLogSanitizer.hashValue(paymentIntentId);
    }
}
