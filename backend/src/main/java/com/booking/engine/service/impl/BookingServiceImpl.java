package com.booking.engine.service.impl;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingCheckoutValidationRequestDto;
import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.PublicBookingHoldResponseDto;
import com.booking.engine.dto.PublicBookingSummaryResponseDto;
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
import com.booking.engine.security.PublicBookingHoldRateLimitService;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.AdminBookingHoldService;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingAdminQueryService;
import com.booking.engine.service.BookingAuditService;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingPaymentSyncService;
import com.booking.engine.service.BookingService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingTransactionalOperations;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.StripePaymentService;
import com.booking.engine.service.payment.BookingPaymentConstants;
import com.booking.engine.validation.BookingValidationMessages;
import com.booking.engine.service.StripePaymentConfirmationResult;
import com.booking.engine.service.StripePaymentIntentDetails;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BookingService}.
 * Provides booking related business operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

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

    private final StripePaymentService stripePaymentService;

    private final BookingValidator bookingValidator;

    private final BookingStateMachine bookingStateMachine;

    private final BookingTransactionalOperations bookingTransactionalOperations;

    private final BookingAuditService bookingAuditService;

    private final BookingAdminQueryService bookingAdminQueryService;

    private final AdminBookingHoldService adminBookingHoldService;

    private final BookingPaymentSyncService bookingPaymentSyncService;

    private final BookingHoldAccessTokenService holdAccessTokenService;

    private final PublicBookingHoldRateLimitService publicBookingHoldRateLimitService;

    // ---------------------- Factories ----------------------

    private final BookingStripeMetadataFactory bookingStripeMetadataFactory;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public PublicBookingSummaryResponseDto getBookingById(UUID id, String holdAccessToken) {
        SlotHoldEntity slotHold = slotHoldRepository.findById(id).orElse(null);
        if (slotHold != null) {
            bookingValidator.validatePublicSlotHoldOwnership(slotHold, holdAccessToken);
            validatePublicSlotHoldAvailability(slotHold);
            return bookingHoldResponseMapper.toPublicSummaryDto(slotHold);
        }

        BookingEntity booking = findBookingOrThrow(id);
        bookingValidator.validatePublicBookingOwnership(booking, holdAccessToken);

        return mapper.toPublicSummaryDto(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BookingResponseDto create(BookingRequestDto request) {
        SlotHoldEntity reservedSlotHold = bookingTransactionalOperations.reserveDirectBookingSlot(request);
        String paymentIntentId = null;

        try {
            paymentIntentId = stripePaymentService.createAndConfirmPayment(
                    reservedSlotHold.getHoldAmount(),
                    reservedSlotHold.getCustomerEmail(),
                    request.getPaymentMethodId(),
                    bookingStripeMetadataFactory.buildStripeMetadata(reservedSlotHold));

            bookingTransactionalOperations.attachStripePaymentToSlotHold(
                    reservedSlotHold.getId(),
                    paymentIntentId,
                    BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED);

            BookingEntity savedBooking = bookingTransactionalOperations.finalizeDirectBookingFromSlotHold(
                    reservedSlotHold.getId(),
                    BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED,
                    BookingPaymentConstants.DIRECT_CREATE_SOURCE);

            log.info("event=booking_created bookingId={} paymentIntentHash={}",
                    savedBooking.getId(), hashPaymentIntentForLogs(paymentIntentId));

            return mapper.toDto(savedBooking);
        } catch (RuntimeException exception) {
            if (paymentIntentId == null) {
                bookingTransactionalOperations.releaseSlotHold(reservedSlotHold.getId());
            } else {
                log.error("event=booking_create_failed_after_payment slotHoldId={} paymentIntentHash={}",
                        reservedSlotHold.getId(),
                        hashPaymentIntentForLogs(paymentIntentId),
                        exception);
            }
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingResponseDto createAdminBooking(AdminBookingCreateRequestDto request) {
        return createAdminBooking(request, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingResponseDto createAdminBooking(
            AdminBookingCreateRequestDto request,
            String adminHoldSessionId) {
        validateBookingTimeRange(request.getStartTime(), request.getEndTime());
        String customerEmail = normalizeOptionalText(request.getCustomerEmail());
        BookingEntity savedBooking;

        if (request.getHoldBookingId() != null) {
            savedBooking = adminBookingHoldService.confirmAdminHeldBooking(request, adminHoldSessionId, customerEmail);
        } else {
            EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
            availabilityService.validateSlotSelection(
                    request.getEmployeeId(),
                    request.getTreatmentId(),
                    request.getBookingDate(),
                    request.getStartTime(),
                    request.getEndTime());
            bookingBlacklistService.validateAllowedCustomer(customerEmail, request.getCustomerPhone());

            TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
            BookingEntity booking = buildAdminBooking(request, employee, treatment, customerEmail);
            savedBooking = bookingRepository.save(booking);
        }

        log.info("event=admin_booking_created bookingId={}", savedBooking.getId());
        auditAdminBookingCreated(savedBooking);
        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PublicBookingHoldResponseDto holdSlot(
            BookingHoldRequestDto request,
            String clientIp,
            String clientDeviceId) {
        validateBookingTimeRange(request.getStartTime(), request.getEndTime());
        publicBookingHoldRateLimitService.registerAttempt(clientIp, clientDeviceId);
        validateHoldLimit(clientIp, clientDeviceId);
        validateGlobalSlotHoldLimit(request);

        EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
        availabilityService.validateSlotSelection(
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
        BigDecimal paymentAmount = treatment.getPrice();
        String holdAccessToken = holdAccessTokenService.generateToken();

        SlotHoldEntity slotHold = buildSlotHold(
                request,
                employee,
                treatment,
                paymentAmount,
                SlotHoldScope.PUBLIC,
                clientIp,
                clientDeviceId,
                LocalDateTime.now(getZoneId()).plusMinutes(BookingHoldConstants.PUBLIC_SLOT_HOLD_MINUTES));
        slotHold.setHoldAccessTokenHash(holdAccessTokenService.hashToken(holdAccessToken));
        SlotHoldEntity savedSlotHold = slotHoldRepository.save(slotHold);

        log.info("event=public_slot_reserved slotHoldId={} expiresAt={}",
                savedSlotHold.getId(), formatLogInstant(savedSlotHold.getExpiresAt()));

        return bookingHoldResponseMapper.toPublicHoldResponseDto(savedSlotHold, holdAccessToken);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingResponseDto holdAdminSlot(BookingHoldRequestDto request, String adminHoldSessionId) {
        return adminBookingHoldService.holdAdminSlot(request, adminHoldSessionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingResponseDto refreshAdminHold(UUID id, String adminHoldSessionId) {
        return adminBookingHoldService.refreshAdminHold(id, adminHoldSessionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void releaseAdminHold(UUID id, String adminHoldSessionId) {
        adminBookingHoldService.releaseAdminHold(id, adminHoldSessionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void validateHeldBookingCheckout(
            UUID id,
            BookingCheckoutValidationRequestDto request,
            String holdAccessToken) {
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            bookingValidator.validatePublicSlotHoldOwnership(slotHold, holdAccessToken);
            validatePublicSlotHoldAvailability(slotHold);
            validatePublicCustomerAllowed(
                    request.getCustomer() != null ? request.getCustomer().getEmail() : null,
                    request.getCustomer() != null ? request.getCustomer().getPhone() : null);
            return;
        }

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        bookingValidator.validatePublicBookingOwnership(booking, holdAccessToken);
        validatePendingBookingAvailability(booking);
        validatePublicCustomerAllowed(
                request.getCustomer() != null ? request.getCustomer().getEmail() : null,
                request.getCustomer() != null ? request.getCustomer().getPhone() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BookingCheckoutSessionResponseDto prepareHeldBookingCheckout(
            UUID id,
            BookingCheckoutSessionRequestDto request,
            String holdAccessToken) {
        log.debug(
                "event=booking_checkout_prepare_requested bookingId={} checkoutConfirmationPresent={} customerEmailMask={}",
                id,
                request.getConfirmationTokenId() != null && !request.getConfirmationTokenId().isBlank(),
                maskCustomerEmailForLogs(request.getCustomer() != null ? request.getCustomer().getEmail() : null));

        BookingTransactionalOperations.CheckoutPreparationTarget target = bookingTransactionalOperations
                .prepareCheckoutTarget(id, request, holdAccessToken);

        if (target.existingPaymentIntentId() != null && !target.existingPaymentIntentId().isBlank()) {
            StripePaymentIntentDetails currentPaymentIntent = stripePaymentService.getPaymentIntentDetails(
                    target.existingPaymentIntentId());
            String currentStatus = currentPaymentIntent.status();
            boolean persistNonSuccessStatus = target
                    .targetType() == BookingTransactionalOperations.CheckoutTargetType.SLOT_HOLD;
            bookingTransactionalOperations.persistCheckoutResult(
                    target,
                    currentPaymentIntent,
                    persistNonSuccessStatus);

            log.debug("event=payment_intent_reused targetType={} targetId={} paymentIntentHash={} paymentStatus={}",
                    target.targetType(),
                    target.targetId(),
                    hashPaymentIntentForLogs(target.existingPaymentIntentId()),
                    currentStatus);

            if (BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(currentStatus)) {
                return BookingCheckoutSessionResponseDto.builder()
                        .paymentIntentId(target.existingPaymentIntentId())
                        .paymentStatus(currentStatus)
                        .build();
            }

            if ("requires_action".equals(currentStatus) || "processing".equals(currentStatus)) {
                throw new BookingValidationException(
                        "Stripe is still processing the payment for this booking. Please finish the current checkout flow.");
            }
        }

        log.debug("event=payment_intent_create_requested targetType={} targetId={} amount={}",
                target.targetType(),
                target.targetId(),
                target.holdAmount());
        StripePaymentConfirmationResult paymentConfirmation = stripePaymentService
                .createAndConfirmPaymentWithConfirmationTokenDetails(
                        target.holdAmount(),
                        target.customerEmail(),
                        request.getConfirmationTokenId(),
                        target.stripeMetadata());
        BookingCheckoutSessionResponseDto checkoutSession = paymentConfirmation.checkoutSession();

        boolean persistNonSuccessStatus = true;
        bookingTransactionalOperations.persistCheckoutResult(
                target,
                paymentConfirmation.paymentIntent(),
                persistNonSuccessStatus);

        log.debug("event=booking_checkout_prepared targetType={} targetId={} paymentIntentHash={} paymentStatus={}",
                target.targetType(),
                target.targetId(),
                hashPaymentIntentForLogs(checkoutSession.getPaymentIntentId()),
                checkoutSession.getPaymentStatus());

        return checkoutSession;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BookingResponseDto confirmHeldBooking(
            UUID id,
            BookingConfirmationRequestDto request,
            String holdAccessToken) {
        bookingTransactionalOperations.validateHeldBookingConfirmationRequest(id, request, holdAccessToken);
        StripePaymentIntentDetails paymentIntent = stripePaymentService.getPaymentIntentDetails(
                request.getPaymentIntentId());
        BookingEntity confirmedBooking = bookingTransactionalOperations.confirmHeldBookingAfterPaymentStatus(
                id,
                request,
                paymentIntent,
                holdAccessToken);
        return mapper.toDto(confirmedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void cancelBooking(UUID id, String holdAccessToken) {
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            bookingValidator.validatePublicSlotHoldOwnership(slotHold, holdAccessToken);
            validatePublicSlotHoldCancellation(slotHold);
            releaseSlotHoldInternal(slotHold);
            log.info("event=public_slot_released slotHoldId={}", id);
            return;
        }

        BookingEntity booking = findBookingOrThrow(id);
        bookingValidator.validatePublicBookingOwnership(booking, holdAccessToken);
        validatePublicCancellation(booking, id);

        cancelPublicBooking(booking);
        bookingRepository.save(booking);

        log.info("event=public_booking_cancelled bookingId={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminBookingListResponseDto getAdminBookings(String search) {
        return bookingAdminQueryService.getAdminBookings(search);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AdminBookingCustomerLookupResponseDto> findLatestCustomerByPhone(String phone) {
        return bookingAdminQueryService.findLatestCustomerByPhone(phone);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingResponseDto updateBookingByAdmin(UUID id, AdminBookingUpdateRequestDto request) {
        validateBookingTimeRange(request.getStartTime(), request.getEndTime());
        BookingEntity booking = findBookingForUpdateOrThrow(id);
        BookingStatus previousStatus = booking.getStatus();
        validateAdminUpdateFinancialFields(booking, request);
        EmployeeEntity employee = findActiveEmployeeForAdminUpdate(request.getEmployeeId());
        TreatmentEntity treatment = findActiveTreatmentOrThrow(request.getTreatmentId());

        if (slotDefinitionChanged(booking, request) && shouldValidateAdminBookingSlot(request.getStatus())) {
            availabilityService.validateSlotSelectionExcludingBooking(
                    request.getEmployeeId(),
                    request.getTreatmentId(),
                    request.getBookingDate(),
                    request.getStartTime(),
                    request.getEndTime(),
                    booking.getId());
        }

        bookingBlacklistService.validateAllowedCustomer(
                normalizeOptionalText(request.getCustomerEmail()),
                normalizeOptionalText(request.getCustomerPhone()));

        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setCustomerName(request.getCustomerName().trim());
        booking.setCustomerPhone(normalizeOptionalText(request.getCustomerPhone()));
        booking.setCustomerEmail(normalizeOptionalText(request.getCustomerEmail()));
        applyAdminUpdateFinancialFields(booking, request);
        applyAdminUpdateState(booking, request.getStatus());

        BookingEntity savedBooking = bookingRepository.save(booking);
        log.info("event=admin_booking_updated bookingId={} status={}",
                savedBooking.getId(),
                savedBooking.getStatus());
        auditAdminBookingUpdated(savedBooking, previousStatus);
        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingResponseDto cancelBookingByAdmin(UUID id) {
        BookingEntity booking = findBookingForUpdateOrThrow(id);
        BookingStatus previousStatus = booking.getStatus();
        validateAdminCancellation(booking);

        cancelByAdmin(booking);

        BookingEntity savedBooking = bookingRepository.save(booking);
        log.info("event=admin_booking_cancelled bookingId={}", id);
        auditAdminBookingCancelled(savedBooking, previousStatus);

        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void syncStripePaymentIntentFromWebhook(String paymentIntentId, String paymentStatus, String eventType) {
        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(paymentIntentId, paymentStatus, eventType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void syncStripePaymentIntentFromWebhook(
            String paymentIntentId,
            String paymentStatus,
            String eventType,
            Map<String, String> metadata) {
        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(paymentIntentId, paymentStatus, eventType,
                metadata);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void syncStripePaymentIntentFromWebhook(StripePaymentIntentDetails paymentIntent, String eventType) {
        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(paymentIntent, eventType);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Locks the employee row before booking flows check whether it can still
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
     * Finds an active employee for admin booking edits without requiring the
     * public-bookable flag, so existing bookings can still be maintained even
     * after team availability settings change.
     */
    private EmployeeEntity findActiveEmployeeForAdminUpdate(UUID employeeId) {
        return employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)
                .orElseThrow(() -> {
                    log.warn("event=employee_lookup_failed reason=not_found employeeId={}", employeeId);
                    return new EntityNotFoundException("Employee", employeeId);
                });
    }

    /*
     * Loads a treatment reference used by booking creation and held-slot
     * confirmation.
     */
    private TreatmentEntity findTreatmentOrThrow(UUID treatmentId) {
        return treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> {
                    log.warn("event=treatment_lookup_failed reason=not_found treatmentId={}", treatmentId);
                    return new EntityNotFoundException("Treatment", treatmentId);
                });
    }

    /*
     * Finds an active treatment for admin booking edits.
     */
    private TreatmentEntity findActiveTreatmentOrThrow(UUID treatmentId) {
        return treatmentRepository.findByIdAndActiveTrue(treatmentId)
                .orElseThrow(() -> {
                    log.warn("event=treatment_lookup_failed reason=not_found_active treatmentId={}", treatmentId);
                    return new EntityNotFoundException("Treatment", treatmentId);
                });
    }

    /*
     * Centralizes booking lookup and not-found logging for public read/cancel
     * paths.
     */
    private BookingEntity findBookingOrThrow(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("event=booking_lookup_failed reason=not_found bookingId={}", id);
                    return new EntityNotFoundException("Booking", id);
                });
    }

    /*
     * Locks the booking row for flows that mutate booking or payment state.
     */
    private BookingEntity findBookingForUpdateOrThrow(UUID id) {
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("event=booking_lookup_failed reason=not_found_for_update bookingId={}", id);
                    return new EntityNotFoundException("Booking", id);
                });
    }

    /*
     * Validates a pending booking can still be processed before the salon decision.
     */
    private void validatePendingBookingAvailability(BookingEntity booking) {
        bookingValidator.validatePendingBookingAvailability(booking);
    }

    /*
     * Public checkout may only use public-facing slot holds.
     */
    private void validatePublicSlotHoldAvailability(SlotHoldEntity slotHold) {
        bookingValidator.validatePublicSlotHoldAvailability(slotHold);
    }

    /*
     * Public cancellation may only release unpaid public slot holds.
     */
    private void validatePublicSlotHoldCancellation(SlotHoldEntity slotHold) {
        bookingValidator.validatePublicSlotHoldCancellation(slotHold);
    }

    /*
     * Validates public cancellation is used only for unpaid temporary holds.
     */
    private void validatePublicCancellation(BookingEntity booking, UUID id) {
        bookingValidator.validatePublicCancellation(booking, id);
    }

    /*
     * Validates admin-side cancellation request.
     */
    private void validateAdminCancellation(BookingEntity booking) {
        bookingValidator.validateAdminCancellation(booking);
    }

    /*
     * Validates admin edits against payment immutability rules.
     */
    private void validateAdminUpdateFinancialFields(BookingEntity booking, AdminBookingUpdateRequestDto request) {
        bookingValidator.validateAdminUpdateFinancialFields(booking, request);
    }

    /*
     * Determines whether an admin edit changed the slot-defining fields and
     * therefore needs a fresh availability validation.
     */
    private boolean slotDefinitionChanged(BookingEntity booking, AdminBookingUpdateRequestDto request) {
        return bookingValidator.slotDefinitionChanged(booking, request);
    }

    /*
     * Determines whether the admin-edited booking status should still reserve
     * the slot on the public website.
     */
    private boolean shouldValidateAdminBookingSlot(BookingStatus status) {
        return bookingValidator.shouldValidateAdminBookingSlot(status);
    }

    /*
     * Normalizes optional free-text values before persisting them.
     */
    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    /*
     * Applies the public cancellation transition without duplicating state
     * machine rules here.
     */
    private void cancelPublicBooking(BookingEntity booking) {
        bookingStateMachine.cancelPublicBooking(booking);
    }

    /*
     * Applies the admin cancellation transition without duplicating state
     * machine rules here.
     */
    private void cancelByAdmin(BookingEntity booking) {
        bookingStateMachine.cancelByAdmin(booking);
    }

    /*
     * Normalizes status-driven admin edits through the booking state machine.
     */
    private void applyAdminUpdateState(BookingEntity booking, BookingStatus status) {
        bookingStateMachine.applyAdminUpdateState(booking, status);
    }

    /*
     * Applies admin financial edits through the state machine so paid amount
     * fields remain unchanged.
     */
    private void applyAdminUpdateFinancialFields(BookingEntity booking, AdminBookingUpdateRequestDto request) {
        bookingStateMachine.applyAdminUpdateFinancialFields(booking, request);
    }

    /*
     * Rejects public bookings from customers whose normalized email or phone is
     * present in the booking blacklist.
     */
    private void validatePublicCustomerAllowed(String email, String phone) {
        bookingValidator.validatePublicCustomerAllowed(email, phone);
    }

    /*
     * Enforces per-IP and per-device limits for unpaid active slot holds to reduce
     * abuse of temporary reservations.
     */
    private void validateHoldLimit(String clientIp, String clientDeviceId) {
        bookingValidator.validateHoldLimit(clientIp, clientDeviceId);
    }

    /*
     * Ensures submitted booking ranges always move forward in time before any
     * booking or availability work proceeds.
     */
    private void validateBookingTimeRange(LocalTime startTime, LocalTime endTime) {
        bookingValidator.validateTimeRange(startTime, endTime);
    }

    /*
     * Enforces a global active-hold ceiling for one public slot before creating a
     * new temporary reservation.
     */
    private void validateGlobalSlotHoldLimit(BookingHoldRequestDto request) {
        bookingValidator.validateGlobalSlotHoldLimit(
                request.getEmployeeId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
    }

    /*
     * Removes a temporary slot hold once it is no longer needed.
     */
    private void releaseSlotHoldInternal(SlotHoldEntity slotHold) {
        bookingStateMachine.releaseSlotHold(slotHold);
    }

    /*
     * Resolves configured booking timezone.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }

    /*
     * Emits admin booking creation audit fields without writing raw contact
     * details to operational logs.
     */
    private void auditAdminBookingCreated(BookingEntity booking) {
        bookingAuditService.auditAdminBookingCreated(booking);
    }

    /*
     * Emits admin booking update audit fields with the previous status retained
     * for review trails.
     */
    private void auditAdminBookingUpdated(BookingEntity booking, BookingStatus previousStatus) {
        bookingAuditService.auditAdminBookingUpdated(booking, previousStatus);
    }

    /*
     * Emits admin booking cancellation audit fields with the previous status
     * retained for review trails.
     */
    private void auditAdminBookingCancelled(BookingEntity booking, BookingStatus previousStatus) {
        bookingAuditService.auditAdminBookingCancelled(booking, previousStatus);
    }

    /*
     * Masks customer email before it is included in diagnostic booking logs.
     */
    private String maskCustomerEmailForLogs(String email) {
        return bookingAuditService.maskCustomerEmailForLogs(email);
    }

    /*
     * Hashes a Stripe PaymentIntent identifier for operational logs.
     */
    private String hashPaymentIntentForLogs(String paymentIntentId) {
        return SensitiveLogSanitizer.hashValue(paymentIntentId);
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
     * Builds a confirmed booking created manually from the admin panel.
     */
    private BookingEntity buildAdminBooking(
            AdminBookingCreateRequestDto request,
            EmployeeEntity employee,
            TreatmentEntity treatment,
            String customerEmail) {
        BookingEntity booking = new BookingEntity();
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
        booking.setHoldAccessTokenHash(null);
        return booking;
    }
}
