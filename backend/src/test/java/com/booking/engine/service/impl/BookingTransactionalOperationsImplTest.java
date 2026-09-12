package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingTransactionalOperations;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.StripePaymentIntentDetails;
import com.booking.engine.service.payment.StripePaymentIntentVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingTransactionalOperationsImplTest {

    private static final String HOLD_ACCESS_TOKEN = "hold-token";

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotHoldRepository slotHoldRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private BookingValidator bookingValidator;

    private BookingTransactionalOperationsImpl bookingTransactionalOperations;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");
        StripeProperties stripeProperties = new StripeProperties();
        stripeProperties.setCurrency("eur");
        StripePaymentIntentVerifier stripePaymentIntentVerifier = new StripePaymentIntentVerifier(stripeProperties);
        BookingStripeMetadataFactory bookingStripeMetadataFactory = new BookingStripeMetadataFactory();

        BookingStateMachine bookingStateMachine = new BookingStateMachineImpl(
                bookingRepository,
                slotHoldRepository,
                bookingProperties);

        bookingTransactionalOperations = new BookingTransactionalOperationsImpl(
                bookingRepository,
                slotHoldRepository,
                employeeRepository,
                treatmentRepository,
                availabilityService,
                bookingValidator,
                bookingStateMachine,
                bookingStripeMetadataFactory,
                stripePaymentIntentVerifier,
                bookingProperties);
    }

    @Test
    void reserveDirectBookingSlotShouldPersistPublicSlotHoldWithCustomerDetails() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID slotHoldId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);
        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = buildTreatment(treatmentId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateBookingRequest(request);
        doNothing().when(bookingValidator).validatePublicCustomerAllowed("john@example.com", "+353870000000");
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            entity.setId(slotHoldId);
            return entity;
        });

        SlotHoldEntity result = bookingTransactionalOperations.reserveDirectBookingSlot(request);

        assertEquals(slotHoldId, result.getId());
        assertEquals(Boolean.TRUE, result.getActive());
        assertEquals(SlotHoldScope.PUBLIC, result.getHoldScope());
        assertEquals(employee, result.getEmployee());
        assertEquals(treatment, result.getTreatment());
        assertEquals(request.getBookingDate(), result.getBookingDate());
        assertEquals(request.getStartTime(), result.getStartTime());
        assertEquals(request.getEndTime(), result.getEndTime());
        assertEquals("John Doe", result.getCustomerName());
        assertEquals("john@example.com", result.getCustomerEmail());
        assertEquals("+353870000000", result.getCustomerPhone());
        assertEquals(new BigDecimal("35.00"), result.getHoldAmount());
        assertNotNull(result.getExpiresAt());
        verify(availabilityService).validateBookingRequest(request);
        verify(bookingValidator).validatePublicCustomerAllowed("john@example.com", "+353870000000");
    }

    @Test
    void reserveDirectBookingSlotShouldRejectInvertedSlotBeforeLookup() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);
        request.setStartTime(LocalTime.of(11, 0));
        request.setEndTime(LocalTime.of(10, 0));
        BookingValidationException validationException = new BookingValidationException(
                "End time must be after start time.");

        doThrow(validationException)
                .when(bookingValidator)
                .validateTimeRange(request.getStartTime(), request.getEndTime());

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingTransactionalOperations.reserveDirectBookingSlot(request));

        assertEquals("End time must be after start time.", exception.getMessage());
        verify(employeeRepository, never()).findByIdAndActiveTrueForUpdate(any(UUID.class));
        verify(availabilityService, never()).validateBookingRequest(any(BookingRequestDto.class));
        verify(slotHoldRepository, never()).save(any(SlotHoldEntity.class));
    }

    @Test
    void prepareCheckoutTargetShouldReturnSlotHoldTargetAndPersistCustomerDetails() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_existing");
        BookingCheckoutSessionRequestDto request = buildCheckoutRequest();

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        doNothing().when(bookingValidator).validatePublicSlotHoldAvailability(slotHold);
        doNothing().when(bookingValidator).validatePublicCustomerAllowed("john@example.com", "+353870000000");
        when(slotHoldRepository.save(slotHold)).thenReturn(slotHold);

        BookingTransactionalOperations.CheckoutPreparationTarget target = bookingTransactionalOperations
                .prepareCheckoutTarget(slotHoldId, request, HOLD_ACCESS_TOKEN);

        assertEquals(BookingTransactionalOperations.CheckoutTargetType.SLOT_HOLD, target.targetType());
        assertEquals(slotHoldId, target.targetId());
        assertEquals("pi_existing", target.existingPaymentIntentId());
        assertEquals(new BigDecimal("35.00"), target.holdAmount());
        assertEquals("john@example.com", target.customerEmail());
        assertEquals(slotHoldId.toString(), target.stripeMetadata().get("slotHoldId"));
        assertEquals("John Doe", slotHold.getCustomerName());
        assertEquals("john@example.com", slotHold.getCustomerEmail());
        verify(bookingValidator).validatePublicSlotHoldOwnership(slotHold, HOLD_ACCESS_TOKEN);
        verify(slotHoldRepository).save(slotHold);
    }

    @Test
    void persistCheckoutResultShouldPersistSucceededSlotHoldPaymentState() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_old");
        slotHold.setPaymentReleasedAt(LocalDateTime.now().minusMinutes(5));
        BookingTransactionalOperations.CheckoutPreparationTarget target = new BookingTransactionalOperations.CheckoutPreparationTarget(
                BookingTransactionalOperations.CheckoutTargetType.SLOT_HOLD,
                slotHoldId,
                "pi_old",
                new BigDecimal("35.00"),
                "john@example.com",
                Map.of());

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        doNothing().when(bookingValidator).validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        when(slotHoldRepository.save(slotHold)).thenReturn(slotHold);

        bookingTransactionalOperations.persistCheckoutResult(
                target,
                paymentIntentDetails(
                        "pi_new",
                        "succeeded",
                        3500L,
                        Map.of("slotHoldId", slotHoldId.toString())),
                true);

        assertEquals("pi_new", slotHold.getStripePaymentIntentId());
        assertEquals("succeeded", slotHold.getStripePaymentStatus());
        assertNotNull(slotHold.getPaymentCapturedAt());
        assertEquals(null, slotHold.getPaymentReleasedAt());
        verify(bookingValidator).validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        verify(slotHoldRepository).save(slotHold);
    }

    @Test
    void confirmHeldBookingAfterPaymentStatusShouldFinalizeSucceededSlotHold() {
        UUID slotHoldId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_slot_hold");
        slotHold.setCustomerName("John Doe");
        slotHold.setCustomerEmail("john@example.com");
        slotHold.setCustomerPhone("+353870000000");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        doNothing().when(bookingValidator).validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        BookingEntity result = bookingTransactionalOperations.confirmHeldBookingAfterPaymentStatus(
                slotHoldId,
                BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_slot_hold")
                        .build(),
                paymentIntentDetails(
                        "pi_slot_hold",
                        "succeeded",
                        3500L,
                        Map.of("slotHoldId", slotHoldId.toString())),
                HOLD_ACCESS_TOKEN);

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals("pi_slot_hold", result.getStripePaymentIntentId());
        assertEquals("succeeded", result.getStripePaymentStatus());
        assertEquals("John Doe", result.getCustomerName());
        assertNotNull(result.getPaymentCapturedAt());
        verify(bookingValidator).validatePublicSlotHoldOwnership(slotHold, HOLD_ACCESS_TOKEN);
        verify(bookingValidator).validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        verify(slotHoldRepository).delete(slotHold);
    }

    @Test
    void confirmHeldBookingAfterPaymentStatusShouldConfirmLegacyBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_legacy");
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");

        when(slotHoldRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.empty());
        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_legacy")).thenReturn(Optional.empty());
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        doNothing().when(bookingValidator).validateBookingCanAcceptSuccessfulPayment(booking);
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingEntity result = bookingTransactionalOperations.confirmHeldBookingAfterPaymentStatus(
                bookingId,
                BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_legacy")
                        .build(),
                paymentIntentDetails(
                        "pi_legacy",
                        "succeeded",
                        3500L,
                        Map.of("bookingId", bookingId.toString())),
                HOLD_ACCESS_TOKEN);

        assertEquals(booking, result);
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        verify(bookingValidator).validatePublicBookingOwnership(booking, HOLD_ACCESS_TOKEN);
        verify(bookingValidator).validateBookingCanAcceptSuccessfulPayment(booking);
        verify(bookingRepository).save(booking);
    }

    @Test
    void attachStripePaymentToSlotHoldShouldPersistSucceededPaymentDetails() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(slotHoldRepository.save(slotHold)).thenReturn(slotHold);

        SlotHoldEntity result = bookingTransactionalOperations.attachStripePaymentToSlotHold(
                slotHoldId,
                "pi_attached",
                "succeeded");

        assertEquals(slotHold, result);
        assertEquals("pi_attached", slotHold.getStripePaymentIntentId());
        assertEquals("succeeded", slotHold.getStripePaymentStatus());
        assertNotNull(slotHold.getPaymentCapturedAt());
        assertEquals(null, slotHold.getPaymentReleasedAt());
        verify(slotHoldRepository).save(slotHold);
    }

    @Test
    void finalizeDirectBookingFromSlotHoldShouldValidateAndFinalizePaidHold() {
        UUID slotHoldId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_direct");
        slotHold.setStripePaymentStatus("succeeded");
        slotHold.setCustomerName("John Doe");
        slotHold.setCustomerEmail("john@example.com");
        slotHold.setCustomerPhone("+353870000000");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        doNothing().when(bookingValidator).validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        BookingEntity result = bookingTransactionalOperations.finalizeDirectBookingFromSlotHold(
                slotHoldId,
                "succeeded",
                "direct_create");

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals("pi_direct", result.getStripePaymentIntentId());
        assertEquals("succeeded", result.getStripePaymentStatus());
        verify(bookingValidator).validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        verify(slotHoldRepository).delete(slotHold);
    }

    private BookingRequestDto buildRequest(UUID employeeId, UUID treatmentId) {
        return BookingRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .paymentMethodId("pm_card_visa")
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .build();
    }

    private BookingCheckoutSessionRequestDto buildCheckoutRequest() {
        return BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .confirmationTokenId("ctoken_123")
                .build();
    }

    private BookingEntity buildPendingBooking(UUID bookingId) {
        BookingEntity booking = new BookingEntity();
        booking.setId(bookingId);
        booking.setActive(true);
        booking.setEmployee(buildActiveBookableEmployee(UUID.randomUUID()));
        booking.setTreatment(buildTreatment(UUID.randomUUID()));
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStartTime(LocalTime.of(10, 0));
        booking.setEndTime(LocalTime.of(11, 0));
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        booking.setHoldAmount(new BigDecimal("35.00"));
        booking.setSlotLocked(false);
        return booking;
    }

    private SlotHoldEntity buildPublicSlotHold(UUID slotHoldId) {
        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setActive(true);
        slotHold.setEmployee(buildActiveBookableEmployee(UUID.randomUUID()));
        slotHold.setTreatment(buildTreatment(UUID.randomUUID()));
        slotHold.setBookingDate(LocalDate.now().plusDays(1));
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(11, 0));
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        slotHold.setHoldAmount(new BigDecimal("35.00"));
        slotHold.setHoldClientIp("203.0.113.10");
        slotHold.setHoldClientDeviceId("device-123");
        return slotHold;
    }

    private EmployeeEntity buildActiveBookableEmployee(UUID employeeId) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setActive(true);
        employee.setBookable(true);
        return employee;
    }

    private TreatmentEntity buildTreatment(UUID treatmentId) {
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));
        return treatment;
    }

    private StripePaymentIntentDetails paymentIntentDetails(
            String paymentIntentId,
            String status,
            Long amount,
            Map<String, String> metadata) {
        return new StripePaymentIntentDetails(paymentIntentId, status, amount, "eur", metadata);
    }
}
