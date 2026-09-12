package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingTransactionalOperations;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.StripePaymentIntentDetails;
import com.booking.engine.service.payment.BookingPaymentConstants;
import com.booking.engine.service.payment.StripePaymentIntentVerifier;
import com.booking.engine.validation.BookingValidationMessages;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BookingTransactionalOperations}.
 * Provides booking transactional operations related business operations.
 */
@Service
@RequiredArgsConstructor
public class BookingTransactionalOperationsImpl implements BookingTransactionalOperations {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(BookingTransactionalOperationsImpl.class);

    private enum ConfirmationTargetType {
        SLOT_HOLD,
        BOOKING,
        CONFIRMED_PAYMENT_MATCH
    }

    private record ConfirmationTarget(
            ConfirmationTargetType targetType,
            SlotHoldEntity slotHold,
            BookingEntity booking) {
    }

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    private final EmployeeRepository employeeRepository;

    private final TreatmentRepository treatmentRepository;

    // ---------------------- Services ----------------------

    private final AvailabilityService availabilityService;

    private final BookingValidator bookingValidator;

    private final BookingStateMachine bookingStateMachine;

    // ---------------------- Factories ----------------------

    public final BookingStripeMetadataFactory bookingStripeMetadataFactory;

    // ---------------------- Verifier ----------------------

    private final StripePaymentIntentVerifier stripePaymentIntentVerifier;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SlotHoldEntity reserveDirectBookingSlot(BookingRequestDto request) {
        bookingValidator.validateTimeRange(request.getStartTime(), request.getEndTime());
        EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
        availabilityService.validateBookingRequest(request);
        bookingValidator.validatePublicCustomerAllowed(
                request.getCustomer().getEmail(),
                request.getCustomer().getPhone());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setActive(true);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(request.getBookingDate());
        slotHold.setStartTime(request.getStartTime());
        slotHold.setEndTime(request.getEndTime());
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setExpiresAt(LocalDateTime.now(getZoneId())
                .plusMinutes(BookingHoldConstants.PUBLIC_SLOT_HOLD_MINUTES));
        slotHold.setHoldAmount(treatment.getPrice());
        slotHold.setCustomerName(request.getCustomer().getName());
        slotHold.setCustomerEmail(request.getCustomer().getEmail());
        slotHold.setCustomerPhone(request.getCustomer().getPhone());
        return slotHoldRepository.save(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public SlotHoldEntity attachStripePaymentToSlotHold(UUID slotHoldId, String paymentIntentId, String paymentStatus) {
        SlotHoldEntity slotHold = findSlotHoldForUpdateOrThrow(slotHoldId);
        slotHold.setStripePaymentIntentId(paymentIntentId);
        slotHold.setStripePaymentStatus(paymentStatus);
        if (BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(paymentStatus)) {
            slotHold.setPaymentCapturedAt(resolveCapturedAt(slotHold));
            slotHold.setPaymentReleasedAt(null);
        }
        return slotHoldRepository.save(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingEntity finalizeDirectBookingFromSlotHold(UUID slotHoldId, String paymentStatus, String source) {
        SlotHoldEntity slotHold = findSlotHoldForUpdateOrThrow(slotHoldId);
        bookingValidator.validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        return bookingStateMachine.finalizePaidSlotHold(slotHold, paymentStatus, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void releaseSlotHold(UUID slotHoldId) {
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(slotHoldId).orElse(null);
        if (slotHold != null) {
            bookingStateMachine.releaseSlotHold(slotHold);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public CheckoutPreparationTarget prepareCheckoutTarget(
            UUID id,
            BookingCheckoutSessionRequestDto request,
            String holdAccessToken) {
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            bookingValidator.validatePublicSlotHoldOwnership(slotHold, holdAccessToken);
            bookingValidator.validatePublicSlotHoldAvailability(slotHold);
            bookingValidator.validatePublicCustomerAllowed(
                    request.getCustomer() != null ? request.getCustomer().getEmail() : null,
                    request.getCustomer() != null ? request.getCustomer().getPhone() : null);
            applyCustomerDetails(slotHold, request.getCustomer());
            slotHoldRepository.save(slotHold);
            return new CheckoutPreparationTarget(
                    CheckoutTargetType.SLOT_HOLD,
                    slotHold.getId(),
                    slotHold.getStripePaymentIntentId(),
                    slotHold.getHoldAmount(),
                    slotHold.getCustomerEmail(),
                    bookingStripeMetadataFactory.buildStripeMetadata(slotHold));
        }

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        bookingValidator.validatePublicBookingOwnership(booking, holdAccessToken);
        bookingValidator.validatePendingBookingAvailability(booking);
        bookingValidator.validatePublicCustomerAllowed(
                request.getCustomer() != null ? request.getCustomer().getEmail() : null,
                request.getCustomer() != null ? request.getCustomer().getPhone() : null);
        applyCustomerDetails(booking, request.getCustomer());
        bookingRepository.save(booking);
        return new CheckoutPreparationTarget(
                CheckoutTargetType.LEGACY_BOOKING,
                booking.getId(),
                booking.getStripePaymentIntentId(),
                booking.getHoldAmount(),
                booking.getCustomerEmail(),
                bookingStripeMetadataFactory.buildStripeMetadata(booking));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void persistCheckoutResult(
            CheckoutPreparationTarget target,
            String paymentIntentId,
            String paymentStatus,
            boolean persistNonSuccessStatus) {
        persistCheckoutResult(
                target,
                new StripePaymentIntentDetails(paymentIntentId, paymentStatus, null, null, Map.of()),
                persistNonSuccessStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void persistCheckoutResult(
            CheckoutPreparationTarget target,
            StripePaymentIntentDetails paymentIntent,
            boolean persistNonSuccessStatus) {
        String paymentIntentId = paymentIntent.paymentIntentId();
        String paymentStatus = paymentIntent.status();

        if (target.targetType() == CheckoutTargetType.SLOT_HOLD) {
            SlotHoldEntity slotHold = findSlotHoldForUpdateOrThrow(target.targetId());
            if (BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(paymentStatus)) {
                validateSuccessfulPaymentIntentForSlotHold(
                        slotHold,
                        paymentIntent,
                        BookingPaymentConstants.CHECKOUT_PREPARATION_SOURCE);
                bookingValidator.validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
            } else {
                bookingValidator.validatePublicSlotHoldAvailability(slotHold);
            }

            if (slotHold.getStripePaymentIntentId() == null
                    || !slotHold.getStripePaymentIntentId().equals(paymentIntentId)) {
                slotHold.setPaymentReleasedAt(null);
            }
            slotHold.setStripePaymentIntentId(paymentIntentId);
            slotHold.setStripePaymentStatus(paymentStatus);
            if (BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(paymentStatus)) {
                slotHold.setPaymentCapturedAt(resolveCapturedAt(slotHold));
            }
            slotHoldRepository.save(slotHold);
            return;
        }

        BookingEntity booking = findBookingForUpdateOrThrow(target.targetId());
        if (BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(paymentStatus)) {
            validateSuccessfulPaymentIntentForBooking(
                    booking,
                    paymentIntent,
                    BookingPaymentConstants.CHECKOUT_PREPARATION_SOURCE);
            bookingValidator.validateBookingCanAcceptSuccessfulPayment(booking);
            booking.setStripePaymentIntentId(paymentIntentId);
            bookingStateMachine.applySuccessfulStripePaymentState(
                    booking,
                    paymentStatus,
                    BookingPaymentConstants.CHECKOUT_PREPARATION_SOURCE);
            bookingRepository.save(booking);
            return;
        }

        if (!persistNonSuccessStatus) {
            return;
        }

        bookingValidator.validatePendingBookingAvailability(booking);
        booking.setStripePaymentIntentId(paymentIntentId);
        booking.setStripePaymentStatus(paymentStatus);
        bookingRepository.save(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void validateHeldBookingConfirmationRequest(
            UUID id,
            BookingConfirmationRequestDto request,
            String holdAccessToken) {
        resolveAndValidateConfirmationTarget(id, request, holdAccessToken);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingEntity confirmHeldBookingAfterPaymentStatus(
            UUID id,
            BookingConfirmationRequestDto request,
            String paymentStatus,
            String holdAccessToken) {
        return confirmHeldBookingAfterPaymentStatus(
                id,
                request,
                new StripePaymentIntentDetails(request.getPaymentIntentId(), paymentStatus, null, null, Map.of()),
                holdAccessToken);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingEntity confirmHeldBookingAfterPaymentStatus(
            UUID id,
            BookingConfirmationRequestDto request,
            StripePaymentIntentDetails paymentIntent,
            String holdAccessToken) {
        String paymentStatus = paymentIntent.status();

        stripePaymentIntentVerifier.requirePaymentIntentIdMatches(
                id,
                request.getPaymentIntentId(),
                paymentIntent,
                "request",
                BookingPaymentConstants.CONFIRM_ENDPOINT_SOURCE);

        ConfirmationTarget target = resolveAndValidateConfirmationTarget(id, request, holdAccessToken);
        if (target.targetType() == ConfirmationTargetType.SLOT_HOLD) {
            SlotHoldEntity slotHold = target.slotHold();
            if (slotHold.getCustomerName() == null || slotHold.getCustomerName().isBlank()
                    || slotHold.getCustomerEmail() == null || slotHold.getCustomerEmail().isBlank()) {
                throw new BookingValidationException(BookingValidationMessages.CHECKOUT_CUSTOMER_DETAILS_MISSING);
            }

            if (BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(paymentStatus)) {
                validateSuccessfulPaymentIntentForSlotHold(
                        slotHold,
                        paymentIntent,
                        BookingPaymentConstants.CONFIRM_ENDPOINT_SOURCE);
                slotHold.setStripePaymentStatus(paymentStatus);
                bookingValidator.validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
                return bookingStateMachine.finalizePaidSlotHold(
                        slotHold,
                        paymentStatus,
                        BookingPaymentConstants.CONFIRM_ENDPOINT_SOURCE);
            }

            slotHold.setStripePaymentStatus(paymentStatus);
            slotHoldRepository.save(slotHold);
            bookingValidator.validateSlotHoldCanFinalizePayment(slotHold);
            throw new BookingValidationException(BookingValidationMessages.STRIPE_PAYMENT_INCOMPLETE);
        }

        if (target.targetType() == ConfirmationTargetType.CONFIRMED_PAYMENT_MATCH) {
            return target.booking();
        }

        BookingEntity booking = target.booking();
        if (booking.getCustomerName() == null || booking.getCustomerName().isBlank()
                || booking.getCustomerEmail() == null || booking.getCustomerEmail().isBlank()) {
            throw new BookingValidationException(BookingValidationMessages.CHECKOUT_CUSTOMER_DETAILS_MISSING);
        }

        if (BookingPaymentConstants.STRIPE_STATUS_SUCCEEDED.equals(paymentStatus)) {
            validateSuccessfulPaymentIntentForBooking(
                    booking,
                    paymentIntent,
                    BookingPaymentConstants.CONFIRM_ENDPOINT_SOURCE);
            bookingValidator.validateBookingCanAcceptSuccessfulPayment(booking);
            bookingStateMachine.applySuccessfulStripePaymentState(
                    booking,
                    paymentStatus,
                    BookingPaymentConstants.CONFIRM_ENDPOINT_SOURCE);
            BookingEntity savedBooking = bookingRepository.save(booking);

            log.info("event=booking_confirmed bookingId={} paymentIntentHash={} status={}",
                    savedBooking.getId(),
                    hashPaymentIntentForLogs(request.getPaymentIntentId()),
                    savedBooking.getStatus());

            return savedBooking;
        }

        booking.setStripePaymentStatus(paymentStatus);
        bookingValidator.validateBookingCanFinalizePayment(booking);
        throw new BookingValidationException(BookingValidationMessages.STRIPE_PAYMENT_INCOMPLETE);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Locks the local confirmation target and validates public ownership plus the
     * persisted Stripe PaymentIntent before the caller talks to Stripe.
     */
    private ConfirmationTarget resolveAndValidateConfirmationTarget(
            UUID id,
            BookingConfirmationRequestDto request,
            String holdAccessToken) {
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            bookingValidator.validatePublicSlotHoldOwnership(slotHold, holdAccessToken);
            validateStripeIntentExists(slotHold);
            validatePersistedPaymentIntentMatchesRequest(slotHold.getStripePaymentIntentId(), request);
            return new ConfirmationTarget(ConfirmationTargetType.SLOT_HOLD, slotHold, null);
        }

        BookingEntity paymentMatchedBooking = bookingRepository.findByStripePaymentIntentIdForUpdate(
                request.getPaymentIntentId()).orElse(null);
        if (paymentMatchedBooking != null
                && !id.equals(paymentMatchedBooking.getId())
                && isConfirmedOrDone(paymentMatchedBooking)) {
            bookingValidator.validatePublicBookingOwnership(paymentMatchedBooking, holdAccessToken);
            validateStripeIntentExists(paymentMatchedBooking);
            validatePersistedPaymentIntentMatchesRequest(paymentMatchedBooking.getStripePaymentIntentId(), request);
            return new ConfirmationTarget(ConfirmationTargetType.CONFIRMED_PAYMENT_MATCH, null, paymentMatchedBooking);
        }

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        bookingValidator.validatePublicBookingOwnership(booking, holdAccessToken);
        validateStripeIntentExists(booking);
        validatePersistedPaymentIntentMatchesRequest(booking.getStripePaymentIntentId(), request);
        return new ConfirmationTarget(ConfirmationTargetType.BOOKING, null, booking);
    }

    private void validatePersistedPaymentIntentMatchesRequest(
            String persistedPaymentIntentId,
            BookingConfirmationRequestDto request) {
        if (!persistedPaymentIntentId.equals(request.getPaymentIntentId())) {
            throw new BookingValidationException(BookingValidationMessages.STRIPE_PAYMENT_MISMATCH);
        }
    }

    private boolean isConfirmedOrDone(BookingEntity booking) {
        return booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.DONE;
    }

    /*
     * Finds an active bookable employee with a write lock or throws when it cannot
     * be booked.
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
     * Finds the requested treatment or throws when it does not exist.
     */
    private TreatmentEntity findTreatmentOrThrow(UUID treatmentId) {
        return treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> {
                    log.warn("event=treatment_lookup_failed reason=not_found treatmentId={}", treatmentId);
                    return new EntityNotFoundException("Treatment", treatmentId);
                });
    }

    /*
     * Finds the booking with a write lock or throws when it does not exist.
     */
    private BookingEntity findBookingForUpdateOrThrow(UUID id) {
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("event=booking_lookup_failed reason=not_found_for_update bookingId={}", id);
                    return new EntityNotFoundException("Booking", id);
                });
    }

    /*
     * Finds the slot hold with a write lock or throws when it does not exist.
     */
    private SlotHoldEntity findSlotHoldForUpdateOrThrow(UUID id) {
        return slotHoldRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("event=slot_hold_lookup_failed reason=not_found_for_update slotHoldId={}", id);
                    return new EntityNotFoundException("SlotHold", id);
                });
    }

    /*
     * Ensures that the target booking resource already has a Stripe payment intent.
     */
    private void validateStripeIntentExists(BookingEntity booking) {
        if (booking.getStripePaymentIntentId() == null || booking.getStripePaymentIntentId().isBlank()) {
            throw new IllegalStateException("Booking has no Stripe payment intent");
        }
    }

    /*
     * Ensures that the target booking resource already has a Stripe payment intent.
     */
    private void validateStripeIntentExists(SlotHoldEntity slotHold) {
        if (slotHold.getStripePaymentIntentId() == null || slotHold.getStripePaymentIntentId().isBlank()) {
            throw new IllegalStateException("Slot hold has no Stripe payment intent");
        }
    }

    /*
     * Verifies all successful Stripe fields before finalizing a legacy held
     * booking.
     */
    private void validateSuccessfulPaymentIntentForBooking(
            BookingEntity booking,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        stripePaymentIntentVerifier.requireSuccessfulForBooking(booking, paymentIntent, source);
    }

    /*
     * Verifies all successful Stripe fields before finalizing a temporary slot
     * hold.
     */
    private void validateSuccessfulPaymentIntentForSlotHold(
            SlotHoldEntity slotHold,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        stripePaymentIntentVerifier.requireSuccessfulForSlotHold(slotHold, paymentIntent, source);
    }

    /*
     * Applies customer details from the request to the payment target.
     */
    private void applyCustomerDetails(BookingEntity booking, BookingRequestDto.CustomerDetailsDto customer) {
        booking.setCustomerName(customer.getName());
        booking.setCustomerEmail(customer.getEmail());
        booking.setCustomerPhone(customer.getPhone());
    }

    /*
     * Applies customer details from the request to the payment target.
     */
    private void applyCustomerDetails(SlotHoldEntity slotHold, BookingRequestDto.CustomerDetailsDto customer) {
        slotHold.setCustomerName(customer.getName());
        slotHold.setCustomerEmail(customer.getEmail());
        slotHold.setCustomerPhone(customer.getPhone());
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
