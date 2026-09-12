package com.booking.engine.service.impl;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.AdminBookingHoldService;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.validation.BookingValidationMessages;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AdminBookingHoldService}.
 * Owns temporary admin-panel hold lifecycle behavior.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBookingHoldServiceImpl implements AdminBookingHoldService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(AdminBookingHoldServiceImpl.class);

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    private final EmployeeRepository employeeRepository;

    private final TreatmentRepository treatmentRepository;

    // ---------------------- Mappers ----------------------

    private final BookingMapper mapper;

    private final BookingHoldResponseMapper bookingHoldResponseMapper;

    // ---------------------- Services ----------------------

    private final AvailabilityService availabilityService;

    private final BookingBlacklistService bookingBlacklistService;

    private final BookingValidator bookingValidator;

    private final BookingStateMachine bookingStateMachine;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingResponseDto holdAdminSlot(BookingHoldRequestDto request, String adminHoldSessionId) {
        bookingValidator.validateTimeRange(request.getStartTime(), request.getEndTime());
        validateAdminHoldSessionId(adminHoldSessionId);
        releaseExistingAdminSessionHolds(adminHoldSessionId, null);

        EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
        availabilityService.validateSlotSelection(
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
        SlotHoldEntity slotHold = buildSlotHold(
                request,
                employee,
                treatment,
                treatment.getPrice(),
                SlotHoldScope.ADMIN,
                BookingStateMachine.ADMIN_HOLD_CLIENT_IP,
                toAdminHoldDeviceId(adminHoldSessionId),
                LocalDateTime.now(getZoneId()).plusMinutes(BookingHoldConstants.ADMIN_SLOT_HOLD_MINUTES));
        SlotHoldEntity savedSlotHold = slotHoldRepository.save(slotHold);

        log.info("event=admin_slot_reserved slotHoldId={} expiresAt={}",
                savedSlotHold.getId(), formatLogInstant(savedSlotHold.getExpiresAt()));
        return bookingHoldResponseMapper.toHoldResponseDto(savedSlotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingResponseDto refreshAdminHold(UUID id, String adminHoldSessionId) {
        validateAdminHoldSessionId(adminHoldSessionId);
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            validateAdminSlotHoldOwnership(slotHold, adminHoldSessionId);
            validateAdminSlotHoldAvailability(slotHold);

            slotHold.setExpiresAt(LocalDateTime.now(getZoneId())
                    .plusMinutes(BookingHoldConstants.ADMIN_SLOT_HOLD_MINUTES));
            SlotHoldEntity savedSlotHold = slotHoldRepository.save(slotHold);
            log.info("event=admin_hold_refreshed entityType=slot_hold slotHoldId={} expiresAt={}",
                    savedSlotHold.getId(),
                    formatLogInstant(savedSlotHold.getExpiresAt()));
            return bookingHoldResponseMapper.toHoldResponseDto(savedSlotHold);
        }

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        validateAdminHoldOwnership(booking, adminHoldSessionId);
        validateAdminHoldAvailability(booking);

        booking.setExpiresAt(LocalDateTime.now(getZoneId())
                .plusMinutes(BookingHoldConstants.ADMIN_SLOT_HOLD_MINUTES));
        BookingEntity savedBooking = bookingRepository.save(booking);
        log.info("event=admin_hold_refreshed entityType=booking bookingId={} expiresAt={}",
                savedBooking.getId(),
                formatLogInstant(savedBooking.getExpiresAt()));

        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void releaseAdminHold(UUID id, String adminHoldSessionId) {
        validateAdminHoldSessionId(adminHoldSessionId);
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            if (!matchesAdminHoldSession(slotHold, adminHoldSessionId)) {
                log.warn("event=admin_hold_release_rejected reason=session_mismatch slotHoldId={}", id);
                return;
            }

            releaseSlotHoldInternal(slotHold);
            log.info("event=admin_hold_released entityType=slot_hold slotHoldId={}", id);
            return;
        }

        BookingEntity booking = bookingRepository.findByIdForUpdate(id).orElse(null);

        if (booking == null) {
            log.debug("event=admin_hold_release_skipped reason=already_released bookingId={}", id);
            return;
        }

        if (!matchesAdminHoldSession(booking, adminHoldSessionId)) {
            log.warn("event=admin_hold_release_rejected reason=session_mismatch bookingId={}", id);
            return;
        }

        releaseAdminHoldInternal(booking);
        log.info("event=admin_hold_released entityType=booking bookingId={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingEntity confirmAdminHeldBooking(
            AdminBookingCreateRequestDto request,
            String adminHoldSessionId,
            String customerEmail) {
        validateAdminHoldSessionId(adminHoldSessionId);

        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(request.getHoldBookingId()).orElse(null);
        if (slotHold != null) {
            validateAdminSlotHoldOwnership(slotHold, adminHoldSessionId);
            validateAdminSlotHoldAvailability(slotHold);

            if (!matchesAdminHeldSlot(slotHold, request)) {
                throw new BookingValidationException(BookingValidationMessages.ADMIN_HOLD_INVALID);
            }

            EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
            bookingBlacklistService.validateAllowedCustomer(customerEmail, request.getCustomerPhone());
            TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());

            BookingEntity booking = new BookingEntity();
            applyConfirmedAdminBookingDetails(booking, request, employee, treatment, customerEmail);
            BookingEntity savedBooking = bookingRepository.save(booking);
            releaseSlotHoldInternal(slotHold);
            return savedBooking;
        }

        BookingEntity booking = findBookingForUpdateOrThrow(request.getHoldBookingId());
        validateAdminHoldOwnership(booking, adminHoldSessionId);
        validateAdminHoldAvailability(booking);

        if (!matchesAdminHeldSlot(booking, request)) {
            throw new BookingValidationException(BookingValidationMessages.ADMIN_HOLD_INVALID);
        }

        EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
        availabilityService.validateSlotSelectionExcludingBooking(
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime(),
                booking.getId());
        bookingBlacklistService.validateAllowedCustomer(customerEmail, request.getCustomerPhone());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
        applyConfirmedAdminBookingDetails(booking, request, employee, treatment, customerEmail);
        return bookingRepository.save(booking);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Locks the employee row before admin hold flows check whether it can still
     * accept public appointments.
     */
    private EmployeeEntity findActiveEmployeeForUpdate(UUID employeeId) {
        EmployeeEntity employee = employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)
                .orElseThrow(() -> {
                    log.warn("event=employee_lookup_failed reason=not_found employeeId={}", employeeId);
                    return new EntityNotFoundException("Employee", employeeId);
                });

        if (!Boolean.TRUE.equals(employee.getBookable())) {
            log.warn("event=employee_lookup_failed reason=not_bookable employeeId={}", employeeId);
            throw new BookingValidationException(BookingValidationMessages.EMPLOYEE_NOT_AVAILABLE_FOR_BOOKING);
        }

        return employee;
    }

    /*
     * Loads a treatment reference used by held-slot confirmation.
     */
    private TreatmentEntity findTreatmentOrThrow(UUID treatmentId) {
        return treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> {
                    log.warn("event=treatment_lookup_failed reason=not_found treatmentId={}", treatmentId);
                    return new EntityNotFoundException("Treatment", treatmentId);
                });
    }

    /*
     * Locks the booking row for flows that mutate admin hold state.
     */
    private BookingEntity findBookingForUpdateOrThrow(UUID id) {
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("event=booking_lookup_failed reason=not_found_for_update bookingId={}", id);
                    return new EntityNotFoundException("Booking", id);
                });
    }

    /*
     * Ensures a non-empty admin hold session id is present before hold endpoints
     * are used.
     */
    private void validateAdminHoldSessionId(String adminHoldSessionId) {
        bookingValidator.validateAdminHoldSessionId(adminHoldSessionId);
    }

    /*
     * Releases any older pending admin holds for the same admin session so only
     * the latest selected slot remains reserved.
     */
    private void releaseExistingAdminSessionHolds(String adminHoldSessionId, UUID keepHoldId) {
        LocalDateTime now = LocalDateTime.now(getZoneId());
        String holdDeviceId = toAdminHoldDeviceId(adminHoldSessionId);

        List<SlotHoldEntity> existingSlotHolds = Optional.ofNullable(
                slotHoldRepository.findActiveByScopeAndHoldClientDeviceId(
                        SlotHoldScope.ADMIN,
                        holdDeviceId,
                        now))
                .orElse(List.of());

        existingSlotHolds.stream()
                .filter(slotHold -> keepHoldId == null || !keepHoldId.equals(slotHold.getId()))
                .forEach(this::releaseSlotHoldInternal);

        List<BookingEntity> existingHolds = Optional.ofNullable(
                bookingRepository.findByHoldClientDeviceIdAndStatus(holdDeviceId, BookingStatus.PENDING))
                .orElse(List.of());

        existingHolds.stream()
                .filter(booking -> keepHoldId == null || !keepHoldId.equals(booking.getId()))
                .forEach(this::releaseAdminHoldInternal);
    }

    /*
     * Verifies that the pending booking belongs to the current admin-panel hold
     * session.
     */
    private void validateAdminHoldOwnership(BookingEntity booking, String adminHoldSessionId) {
        bookingValidator.validateAdminHoldOwnership(booking, adminHoldSessionId);
    }

    /*
     * Verifies that the temporary slot hold belongs to the current admin-panel
     * session.
     */
    private void validateAdminSlotHoldOwnership(SlotHoldEntity slotHold, String adminHoldSessionId) {
        bookingValidator.validateAdminSlotHoldOwnership(slotHold, adminHoldSessionId);
    }

    /*
     * Verifies that the admin hold is still the same kind of unpaid pending slot
     * reservation created from the admin booking page.
     */
    private void validateAdminHoldAvailability(BookingEntity booking) {
        bookingValidator.validateAdminHoldAvailability(booking);
    }

    /*
     * Verifies that the admin slot hold is still active and still owns a free
     * slot.
     */
    private void validateAdminSlotHoldAvailability(SlotHoldEntity slotHold) {
        bookingValidator.validateAdminSlotHoldAvailability(slotHold);
    }

    /*
     * Determines whether a booking belongs to the current admin hold session.
     */
    private boolean matchesAdminHoldSession(BookingEntity booking, String adminHoldSessionId) {
        return bookingStateMachine.matchesAdminHoldSession(booking, adminHoldSessionId);
    }

    /*
     * Determines whether a slot hold belongs to the current admin hold session.
     */
    private boolean matchesAdminHoldSession(SlotHoldEntity slotHold, String adminHoldSessionId) {
        return bookingStateMachine.matchesAdminHoldSession(slotHold, adminHoldSessionId);
    }

    /*
     * Releases an admin-panel hold without affecting already confirmed bookings.
     */
    private void releaseAdminHoldInternal(BookingEntity booking) {
        bookingStateMachine.releaseAdminHold(booking);
    }

    /*
     * Removes a temporary slot hold once it is no longer needed.
     */
    private void releaseSlotHoldInternal(SlotHoldEntity slotHold) {
        bookingStateMachine.releaseSlotHold(slotHold);
    }

    /*
     * Maps an admin hold session id to the booking hold device token stored in the
     * database.
     */
    private String toAdminHoldDeviceId(String adminHoldSessionId) {
        return bookingStateMachine.toAdminHoldDeviceId(adminHoldSessionId);
    }

    /*
     * Resolves configured booking timezone.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }

    /*
     * Formats booking-local timestamps as explicit UTC values for logs.
     */
    private String formatLogInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(getZoneId()).toInstant().toString();
    }

    /*
     * Builds a temporary slot hold entity from selection data.
     */
    private SlotHoldEntity buildSlotHold(
            BookingHoldRequestDto request,
            EmployeeEntity employee,
            TreatmentEntity treatment,
            BigDecimal paymentAmount,
            SlotHoldScope holdScope,
            String clientIp,
            String clientDeviceId,
            LocalDateTime expiresAt) {
        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setActive(true);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(request.getBookingDate());
        slotHold.setStartTime(request.getStartTime());
        slotHold.setEndTime(request.getEndTime());
        slotHold.setHoldScope(holdScope);
        slotHold.setExpiresAt(expiresAt);
        slotHold.setHoldAmount(paymentAmount);
        slotHold.setHoldClientIp(clientIp);
        slotHold.setHoldClientDeviceId(clientDeviceId);
        return slotHold;
    }

    /*
     * Ensures the admin-confirm action still refers to the same held slot.
     */
    private boolean matchesAdminHeldSlot(BookingEntity booking, AdminBookingCreateRequestDto request) {
        return bookingValidator.matchesAdminHeldSlot(booking, request);
    }

    /*
     * Ensures the admin-confirm action still refers to the same temporary slot
     * hold.
     */
    private boolean matchesAdminHeldSlot(SlotHoldEntity slotHold, AdminBookingCreateRequestDto request) {
        return bookingValidator.matchesAdminHeldSlot(slotHold, request);
    }

    /*
     * Applies the final confirmed state for a phone booking that started as an
     * admin hold.
     */
    private void applyConfirmedAdminBookingDetails(
            BookingEntity booking,
            AdminBookingCreateRequestDto request,
            EmployeeEntity employee,
            TreatmentEntity treatment,
            String customerEmail) {
        bookingStateMachine.applyConfirmedAdminBookingDetails(booking, request, employee, treatment, customerEmail);
    }
}
