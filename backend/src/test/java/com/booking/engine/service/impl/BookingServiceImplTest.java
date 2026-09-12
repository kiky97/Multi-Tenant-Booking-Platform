package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingCheckoutValidationRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.PublicBookingHoldResponseDto;
import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.exception.PaymentProcessingException;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.security.PublicBookingHoldRateLimitService;
import com.booking.engine.security.SecurityAuditEvent;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingAdminQueryService;
import com.booking.engine.service.BookingAuditService;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingTransactionalOperations;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.StripePaymentService;
import com.booking.engine.service.payment.StripePaymentIntentVerifier;
import com.booking.engine.service.StripePaymentConfirmationResult;
import com.booking.engine.service.StripePaymentIntentDetails;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final String HOLD_ACCESS_TOKEN = "hold-token";
    private static final String TEST_TIMEZONE = "Europe/Dublin";
    private static final LocalDate ACTIVE_BOOKING_DATE = LocalDate.of(2099, 1, 15);
    private static final LocalDateTime ACTIVE_HOLD_EXPIRY = LocalDateTime.of(2099, 1, 15, 12, 0);
    private static final LocalDateTime EXPIRED_HOLD_EXPIRY = LocalDateTime.of(2000, 1, 15, 12, 0);
    private static final LocalDateTime PAST_PAYMENT_TIMESTAMP = LocalDateTime.of(2020, 1, 15, 12, 0);

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
    private BookingBlacklistService bookingBlacklistService;

    @Mock
    private StripePaymentService stripePaymentService;

    @Mock
    private BookingMapper mapper;

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    private BookingServiceImpl bookingService;
    private BookingHoldAccessTokenService holdAccessTokenService;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone(TEST_TIMEZONE);
        StripeProperties stripeProperties = new StripeProperties();
        stripeProperties.setCurrency("eur");

        StripePaymentIntentVerifier stripePaymentIntentVerifier = new StripePaymentIntentVerifier(stripeProperties);

        BookingStripeMetadataFactory bookingStripeMetadataFactory = new BookingStripeMetadataFactory();

        BookingStateMachine bookingStateMachine = new BookingStateMachineImpl(
                bookingRepository,
                slotHoldRepository,
                bookingProperties);
        holdAccessTokenService = new BookingHoldAccessTokenService();
        BookingValidator bookingValidator = new BookingValidatorImpl(
                bookingRepository,
                slotHoldRepository,
                bookingBlacklistService,
                bookingStateMachine,
                holdAccessTokenService,
                bookingProperties);
        BookingTransactionalOperations bookingTransactionalOperations = new BookingTransactionalOperationsImpl(
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
        BookingAuditService bookingAuditService = new BookingAuditServiceImpl(securityAuditLogger);
        BookingAdminQueryService bookingAdminQueryService = new BookingAdminQueryServiceImpl(
                bookingRepository,
                mapper,
                bookingAuditService,
                bookingProperties);
        BookingHoldResponseMapper bookingHoldResponseMapper = new BookingHoldResponseMapper();
        AdminBookingHoldServiceImpl adminBookingHoldService = new AdminBookingHoldServiceImpl(
                bookingRepository,
                slotHoldRepository,
                employeeRepository,
                treatmentRepository,
                mapper,
                bookingHoldResponseMapper,
                availabilityService,
                bookingBlacklistService,
                bookingValidator,
                bookingStateMachine,
                bookingProperties);
        BookingPaymentSyncServiceImpl bookingPaymentSyncService = new BookingPaymentSyncServiceImpl(
                bookingRepository,
                slotHoldRepository,
                bookingValidator,
                bookingStateMachine,
                stripePaymentIntentVerifier);
        PublicBookingHoldRateLimitService publicBookingHoldRateLimitService = new PublicBookingHoldRateLimitService(
                bookingProperties,
                Clock.systemUTC());

        bookingService = new BookingServiceImpl(
                bookingRepository,
                slotHoldRepository,
                employeeRepository,
                treatmentRepository,
                mapper,
                bookingHoldResponseMapper,
                availabilityService,
                bookingBlacklistService,
                stripePaymentService,
                bookingValidator,
                bookingStateMachine,
                bookingTransactionalOperations,
                bookingAuditService,
                bookingAdminQueryService,
                adminBookingHoldService,
                bookingPaymentSyncService,
                holdAccessTokenService,
                publicBookingHoldRateLimitService,
                bookingStripeMetadataFactory,
                bookingProperties);

        org.mockito.Mockito.lenient().when(slotHoldRepository.findByIdForUpdate(any(UUID.class)))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(slotHoldRepository.findById(any(UUID.class)))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(slotHoldRepository.findByStripePaymentIntentIdForUpdate(any(String.class)))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                any(UUID.class),
                any(LocalDate.class),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(slotHoldRepository.findActiveByScopeAndHoldClientDeviceId(
                any(),
                any(String.class),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(slotHoldRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(securityAuditLogger.event(anyString(), anyString()))
                .thenAnswer(invocation -> SecurityAuditEvent.builder()
                        .eventType(invocation.getArgument(0))
                        .outcome(invocation.getArgument(1)));
    }

    @Test
    void createShouldPersistPaidConfirmedBooking() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID slotHoldId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));
        AtomicReference<SlotHoldEntity> reservedSlotHoldRef = new AtomicReference<>();

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateBookingRequest(request);
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(slotHoldId);
            }
            reservedSlotHoldRef.set(entity);
            return entity;
        });
        when(slotHoldRepository.findByIdForUpdate(slotHoldId))
                .thenAnswer(invocation -> Optional.ofNullable(reservedSlotHoldRef.get()));
        when(stripePaymentService.createAndConfirmPayment(any(), any(), any(), any())).thenReturn("pi_paid");
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });
        when(mapper.toDto(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            return BookingResponseDto.builder()
                    .id(entity.getId())
                    .status(entity.getStatus())
                    .stripePaymentIntentId(entity.getStripePaymentIntentId())
                    .build();
        });

        BookingResponseDto result = bookingService.create(request);
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals("pi_paid", result.getStripePaymentIntentId());
        verify(stripePaymentService).createAndConfirmPayment(
                amountCaptor.capture(),
                any(),
                any(),
                metadataCaptor.capture());
        verify(bookingRepository).save(bookingCaptor.capture());
        assertEquals(new BigDecimal("35.00"), amountCaptor.getValue());
        assertEquals(slotHoldId.toString(), metadataCaptor.getValue().get("slotHoldId"));
        assertEquals(request.getEmployeeId().toString(), metadataCaptor.getValue().get("employeeId"));
        assertEquals(request.getTreatmentId().toString(), metadataCaptor.getValue().get("treatmentId"));
        assertEquals(request.getBookingDate().toString(), metadataCaptor.getValue().get("bookingDate"));
        assertEquals(request.getStartTime().toString(), metadataCaptor.getValue().get("startTime"));
        assertEquals(request.getEndTime().toString(), metadataCaptor.getValue().get("endTime"));
        org.junit.jupiter.api.Assertions.assertFalse(metadataCaptor.getValue().containsKey("customerEmail"));
        assertEquals("pi_paid", reservedSlotHoldRef.get().getStripePaymentIntentId());
        assertEquals("succeeded", reservedSlotHoldRef.get().getStripePaymentStatus());
        assertNotNull(reservedSlotHoldRef.get().getPaymentCapturedAt());

        BookingEntity persistedBooking = bookingCaptor.getValue();
        assertEquals(BookingStatus.CONFIRMED, persistedBooking.getStatus());
        assertEquals("John Doe", persistedBooking.getCustomerName());
        assertEquals("john@example.com", persistedBooking.getCustomerEmail());
        assertEquals("+353870000000", persistedBooking.getCustomerPhone());
        assertEquals(new BigDecimal("35.00"), persistedBooking.getHoldAmount());
        assertEquals("pi_paid", persistedBooking.getStripePaymentIntentId());
        assertEquals("succeeded", persistedBooking.getStripePaymentStatus());
        assertNotNull(persistedBooking.getPaymentCapturedAt());
        assertEquals(null, persistedBooking.getExpiresAt());
        verify(slotHoldRepository).delete(reservedSlotHoldRef.get());
    }

    @Test
    void createShouldReleaseReservedSlotHoldWhenStripePaymentFailsBeforePaymentIntentExists() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID slotHoldId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));
        AtomicReference<SlotHoldEntity> reservedSlotHoldRef = new AtomicReference<>();

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateBookingRequest(request);
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(slotHoldId);
            }
            reservedSlotHoldRef.set(entity);
            return entity;
        });
        when(slotHoldRepository.findByIdForUpdate(slotHoldId))
                .thenAnswer(invocation -> Optional.ofNullable(reservedSlotHoldRef.get()));
        when(stripePaymentService.createAndConfirmPayment(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("stripe unavailable"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> bookingService.create(request));

        assertEquals("stripe unavailable", exception.getMessage());
        verify(slotHoldRepository).delete(reservedSlotHoldRef.get());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void createShouldKeepReservedSlotHoldForWebhookReconciliationWhenFinalizationFailsAfterPaymentPersistence() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID slotHoldId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));
        AtomicReference<SlotHoldEntity> reservedSlotHoldRef = new AtomicReference<>();

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateBookingRequest(request);
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(slotHoldId);
            }
            reservedSlotHoldRef.set(entity);
            return entity;
        });
        when(slotHoldRepository.findByIdForUpdate(slotHoldId))
                .thenAnswer(invocation -> Optional.ofNullable(reservedSlotHoldRef.get()));
        when(stripePaymentService.createAndConfirmPayment(any(), any(), any(), any())).thenReturn("pi_paid");
        when(bookingRepository.save(any(BookingEntity.class))).thenThrow(new RuntimeException("database write failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> bookingService.create(request));

        assertEquals("database write failed", exception.getMessage());
        assertEquals("pi_paid", reservedSlotHoldRef.get().getStripePaymentIntentId());
        assertEquals("succeeded", reservedSlotHoldRef.get().getStripePaymentStatus());
        assertNotNull(reservedSlotHoldRef.get().getPaymentCapturedAt());
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void createAdminBookingShouldPersistConfirmedBookingWithoutStripe() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(ACTIVE_BOOKING_DATE)
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .customerEmail("phone@example.com")
                .build();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("28.00"));

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("phone@example.com", "+353831234567");
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });
        when(mapper.toDto(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            return BookingResponseDto.builder()
                    .id(entity.getId())
                    .status(entity.getStatus())
                    .customerName(entity.getCustomerName())
                    .customerPhone(entity.getCustomerPhone())
                    .customerEmail(entity.getCustomerEmail())
                    .holdAmount(entity.getHoldAmount())
                    .build();
        });

        BookingResponseDto result = bookingService.createAdminBooking(request);

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals("Phone Client", result.getCustomerName());
        assertEquals("+353831234567", result.getCustomerPhone());
        assertEquals("phone@example.com", result.getCustomerEmail());
        assertEquals(new BigDecimal("28.00"), result.getHoldAmount());

        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        BookingEntity persisted = bookingCaptor.getValue();
        assertEquals(BookingStatus.CONFIRMED, persisted.getStatus());
        assertEquals(new BigDecimal("28.00"), persisted.getHoldAmount());
        assertEquals("Phone Client", persisted.getCustomerName());
        assertEquals("+353831234567", persisted.getCustomerPhone());
        assertEquals("phone@example.com", persisted.getCustomerEmail());
        assertEquals(null, persisted.getStripePaymentIntentId());
        assertEquals(null, persisted.getStripePaymentStatus());
        assertEquals(null, persisted.getExpiresAt());
        verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "ADMIN_BOOKING_CREATE".equals(event.getEventType())
                        && bookingId.toString().equals(event.getResourceId())));
    }

    @Test
    void createAdminBookingShouldPromoteMatchingAdminHoldIntoConfirmedBooking() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(ACTIVE_BOOKING_DATE)
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .holdBookingId(holdId)
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .customerEmail("phone@example.com")
                .build();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("28.00"));

        BookingEntity heldBooking = buildPendingBooking(holdId);
        heldBooking.setActive(false);
        heldBooking.setEmployee(employee);
        heldBooking.setTreatment(treatment);
        heldBooking.setBookingDate(request.getBookingDate());
        heldBooking.setStartTime(request.getStartTime());
        heldBooking.setEndTime(request.getEndTime());
        heldBooking.setHoldClientIp("admin-panel");
        heldBooking.setHoldClientDeviceId("admin-panel:session-123");

        when(bookingRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(heldBooking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                eq(employeeId),
                eq(request.getBookingDate()),
                eq(List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
                        BookingStatus.DONE))))
                .thenReturn(List.of(heldBooking));
        doNothing().when(availabilityService).validateSlotSelectionExcludingBooking(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime(),
                holdId);
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("phone@example.com", "+353831234567");
        when(bookingRepository.save(heldBooking)).thenReturn(heldBooking);
        when(mapper.toDto(heldBooking)).thenReturn(BookingResponseDto.builder()
                .id(holdId)
                .status(BookingStatus.CONFIRMED)
                .customerName("Phone Client")
                .build());

        BookingResponseDto result = bookingService.createAdminBooking(request, "session-123");

        assertEquals(BookingStatus.CONFIRMED, heldBooking.getStatus());
        assertEquals(Boolean.TRUE, heldBooking.getActive());
        assertEquals("Phone Client", heldBooking.getCustomerName());
        assertEquals("+353831234567", heldBooking.getCustomerPhone());
        assertEquals("phone@example.com", heldBooking.getCustomerEmail());
        assertEquals(new BigDecimal("28.00"), heldBooking.getHoldAmount());
        assertEquals(null, heldBooking.getExpiresAt());
        assertEquals(null, heldBooking.getHoldClientIp());
        assertEquals(null, heldBooking.getHoldClientDeviceId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void findLatestCustomerByPhoneShouldReturnLatestExactMatch() {
        BookingEntity newestMatch = new BookingEntity();
        newestMatch.setCustomerName("Repeat Client");
        newestMatch.setCustomerPhone("+353 83 123 4567");
        newestMatch.setCustomerEmail("repeat@example.com");

        BookingEntity otherBooking = new BookingEntity();
        otherBooking.setCustomerName("Someone Else");
        otherBooking.setCustomerPhone("+353 85 000 0000");

        when(bookingRepository.findAllByActiveTrueAndCustomerPhoneIsNotNullOrderByCreatedAtDesc())
                .thenReturn(java.util.List.of(newestMatch, otherBooking));

        AdminBookingCustomerLookupResponseDto result = bookingService.findLatestCustomerByPhone("+353831234567")
                .orElseThrow();

        assertEquals("Repeat Client", result.getCustomerName());
        assertEquals("+353 83 123 4567", result.getCustomerPhone());
        assertEquals("repeat@example.com", result.getCustomerEmail());
    }

    @Test
    void findLatestCustomerByPhoneShouldReturnEmptyForBlankOrUnknownPhone() {
        when(bookingRepository.findAllByActiveTrueAndCustomerPhoneIsNotNullOrderByCreatedAtDesc())
                .thenReturn(java.util.List.of());

        assertEquals(true, bookingService.findLatestCustomerByPhone("   ").isEmpty());
        assertEquals(true, bookingService.findLatestCustomerByPhone("+353000000000").isEmpty());
    }

    @Test
    void createShouldRejectBlacklistedCustomerWithGenericPublicMessage() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateBookingRequest(request);
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(true);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.create(request));

        assertEquals(
                "Booking through the website is temporarily unavailable. Please contact the barbershop directly.",
                exception.getMessage());
        verify(stripePaymentService, never()).createAndConfirmPayment(any(), any(), any(), any());
    }

    @Test
    void createShouldPropagateUnsupportedEmployeeTreatmentValidation() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);
        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        org.mockito.Mockito
                .doThrow(new BookingValidationException("This employee does not provide the selected service."))
                .when(availabilityService)
                .validateBookingRequest(request);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.create(request));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void holdSlotShouldPersistPendingBookingWithExpiry() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        PublicBookingHoldResponseDto result = bookingService.holdSlot(request, "203.0.113.10", "device-123");

        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertNotNull(result.getExpiresAt());
        assertNotNull(result.getHoldAccessToken());

        ArgumentCaptor<SlotHoldEntity> holdCaptor = ArgumentCaptor.forClass(SlotHoldEntity.class);
        verify(slotHoldRepository).save(holdCaptor.capture());
        SlotHoldEntity savedHold = holdCaptor.getValue();
        assertEquals("203.0.113.10", savedHold.getHoldClientIp());
        assertEquals("device-123", savedHold.getHoldClientDeviceId());
        assertEquals(new BigDecimal("35.00"), savedHold.getHoldAmount());
        assertNotNull(savedHold.getHoldAccessTokenHash());
        assertNotEquals(result.getHoldAccessToken(), savedHold.getHoldAccessTokenHash());
        org.junit.jupiter.api.Assertions.assertTrue(
                holdAccessTokenService.matches(result.getHoldAccessToken(), savedHold.getHoldAccessTokenHash()));
    }

    @Test
    void holdSlotShouldRejectZeroLengthSlot() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);
        request.setEndTime(request.getStartTime());

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals("End time must be after start time.", exception.getMessage());
        verify(employeeRepository, never()).findByIdAndActiveTrueForUpdate(any(UUID.class));
        verify(slotHoldRepository, never()).save(any(SlotHoldEntity.class));
    }

    @Test
    void holdAdminSlotShouldPersistInactivePendingBookingWithAdminSession() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        when(bookingRepository.findByHoldClientDeviceIdAndStatus("admin-panel:session-123", BookingStatus.PENDING))
                .thenReturn(List.of());
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        BookingResponseDto result = bookingService.holdAdminSlot(request, "session-123");

        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertNotNull(result.getExpiresAt());
        assertEquals(new BigDecimal("35.00"), result.getHoldAmount());

        ArgumentCaptor<SlotHoldEntity> holdCaptor = ArgumentCaptor.forClass(SlotHoldEntity.class);
        verify(slotHoldRepository).save(holdCaptor.capture());
        assertEquals(Boolean.TRUE, holdCaptor.getValue().getActive());
        assertEquals("admin-panel", holdCaptor.getValue().getHoldClientIp());
        assertEquals("admin-panel:session-123", holdCaptor.getValue().getHoldClientDeviceId());
    }

    @Test
    void holdAdminSlotShouldReleasePreviousAdminSessionSlotHoldBeforeSavingNewOne() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        SlotHoldEntity existingHold = new SlotHoldEntity();
        existingHold.setId(UUID.randomUUID());
        existingHold.setActive(true);
        existingHold.setHoldScope(SlotHoldScope.ADMIN);
        existingHold.setHoldClientDeviceId("admin-panel:session-123");
        existingHold.setStartTime(LocalTime.of(9, 0));
        existingHold.setEndTime(LocalTime.of(10, 0));

        when(slotHoldRepository.findActiveByScopeAndHoldClientDeviceId(
                eq(SlotHoldScope.ADMIN),
                eq("admin-panel:session-123"),
                any(LocalDateTime.class)))
                .thenReturn(List.of(existingHold));
        when(bookingRepository.findByHoldClientDeviceIdAndStatus("admin-panel:session-123", BookingStatus.PENDING))
                .thenReturn(List.of());
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        bookingService.holdAdminSlot(request, "session-123");

        verify(slotHoldRepository).delete(existingHold);
        verify(slotHoldRepository).save(any(SlotHoldEntity.class));
    }

    @Test
    void refreshAdminHoldShouldExtendPendingAdminSessionHold() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setActive(false);
        booking.setHoldClientIp("admin-panel");
        booking.setHoldClientDeviceId("admin-panel:session-123");
        LocalDateTime previousExpiry = refreshableHoldExpiry();
        booking.setExpiresAt(previousExpiry);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                eq(booking.getEmployee().getId()),
                eq(booking.getBookingDate()),
                eq(List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
                        BookingStatus.DONE))))
                .thenReturn(List.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.PENDING)
                .expiresAt(booking.getExpiresAt())
                .build());

        BookingResponseDto result = bookingService.refreshAdminHold(bookingId, "session-123");

        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertNotNull(booking.getExpiresAt());
        org.junit.jupiter.api.Assertions.assertTrue(booking.getExpiresAt().isAfter(previousExpiry));
    }

    @Test
    void releaseAdminHoldShouldCancelAndDeactivateMatchingPendingAdminHold() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setActive(false);
        booking.setHoldClientIp("admin-panel");
        booking.setHoldClientDeviceId("admin-panel:session-123");

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.releaseAdminHold(bookingId, "session-123");

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(Boolean.FALSE, booking.getActive());
        assertEquals(null, booking.getExpiresAt());
        assertEquals(null, booking.getHoldClientIp());
        assertEquals(null, booking.getHoldClientDeviceId());
    }

    @Test
    void refreshAdminHoldShouldExtendMatchingSlotHold() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildAdminSlotHold(slotHoldId, "session-123");
        LocalDateTime previousExpiry = refreshableHoldExpiry();
        slotHold.setExpiresAt(previousExpiry);

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));

        BookingResponseDto result = bookingService.refreshAdminHold(slotHoldId, "session-123");

        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertEquals(slotHoldId, result.getId());
        assertNotNull(slotHold.getExpiresAt());
        org.junit.jupiter.api.Assertions.assertTrue(slotHold.getExpiresAt().isAfter(previousExpiry));
        verify(slotHoldRepository).save(slotHold);
        verify(bookingRepository, never()).findByIdForUpdate(slotHoldId);
    }

    @Test
    void releaseAdminHoldShouldDeleteMatchingSlotHold() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildAdminSlotHold(slotHoldId, "session-123");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));

        bookingService.releaseAdminHold(slotHoldId, "session-123");

        verify(slotHoldRepository).delete(slotHold);
        verify(bookingRepository, never()).findByIdForUpdate(slotHoldId);
    }

    @Test
    void holdAdminSlotShouldReleasePreviousAdminSessionBookingHoldBeforeSavingNewOne() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        BookingEntity existingHold = buildPendingBooking(UUID.randomUUID());
        existingHold.setActive(true);
        existingHold.setHoldClientIp("admin-panel");
        existingHold.setHoldClientDeviceId("admin-panel:session-123");

        when(bookingRepository.findByHoldClientDeviceIdAndStatus("admin-panel:session-123", BookingStatus.PENDING))
                .thenReturn(List.of(existingHold));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        bookingService.holdAdminSlot(request, "session-123");

        assertEquals(BookingStatus.CANCELLED, existingHold.getStatus());
        assertEquals(Boolean.FALSE, existingHold.getActive());
        assertEquals(null, existingHold.getExpiresAt());
        assertEquals(null, existingHold.getHoldClientIp());
        assertEquals(null, existingHold.getHoldClientDeviceId());
        assertNotNull(existingHold.getPaymentReleasedAt());
        verify(bookingRepository).save(existingHold);
        verify(slotHoldRepository).save(any(SlotHoldEntity.class));
    }

    @Test
    void holdSlotShouldRejectNonBookableEmployee() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        employee.setBookable(false);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals("Employee is not available for booking", exception.getMessage());
        verify(availabilityService, never()).validateSlotSelection(any(), any(), any(), any(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void holdSlotShouldRejectUnsupportedEmployeeTreatmentPair() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);
        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        org.mockito.Mockito
                .doThrow(new BookingValidationException("This employee does not provide the selected service."))
                .when(availabilityService)
                .validateSlotSelection(
                        employeeId,
                        treatmentId,
                        request.getBookingDate(),
                        request.getStartTime(),
                        request.getEndTime());

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void publicBookingReadShouldRejectMissingHoldAccessToken() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.getBookingById(bookingId, null));

        assertEquals("This appointment hold is no longer available.", exception.getMessage());
        verify(mapper, never()).toPublicSummaryDto(any(BookingEntity.class));
    }

    @Test
    void publicHoldReadShouldAllowCorrectHoldAccessToken() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);

        when(slotHoldRepository.findById(slotHoldId)).thenReturn(Optional.of(slotHold));

        var result = bookingService.getBookingById(slotHoldId, HOLD_ACCESS_TOKEN);

        assertEquals(slotHoldId, result.getId());
        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertEquals(slotHold.getEmployee().getId(), result.getEmployeeId());
        assertEquals(slotHold.getTreatment().getId(), result.getTreatmentId());
        verify(bookingRepository, never()).findById(slotHoldId);
    }

    @Test
    void validateHeldBookingCheckoutShouldAllowEligibleCustomerBeforeOpeningStripe() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingCheckoutValidationRequestDto request = BookingCheckoutValidationRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);

        bookingService.validateHeldBookingCheckout(bookingId, request, HOLD_ACCESS_TOKEN);

        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void validateHeldBookingCheckoutShouldRejectMissingHoldAccessToken() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingCheckoutValidationRequestDto request = BookingCheckoutValidationRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.validateHeldBookingCheckout(bookingId, request, null));

        assertEquals("This appointment hold is no longer available.", exception.getMessage());
        verify(bookingBlacklistService, never()).isBlockedCustomer(any(), any());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void validateHeldBookingCheckoutShouldRejectBlacklistedCustomerBeforeStripe() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingCheckoutValidationRequestDto request = BookingCheckoutValidationRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("Blocked Customer")
                        .email("blocked@example.com")
                        .phone("+353870000000")
                        .build())
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingBlacklistService.isBlockedCustomer("blocked@example.com", "+353870000000")).thenReturn(true);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.validateHeldBookingCheckout(bookingId, request, HOLD_ACCESS_TOKEN));

        assertEquals(
                "Booking through the website is temporarily unavailable. Please contact the barbershop directly.",
                exception.getMessage());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void validateHeldBookingCheckoutShouldRejectWhenSlotWasTakenByDoneBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingEntity conflictingBooking = buildConflictingBooking(booking, BookingStatus.DONE);
        BookingCheckoutValidationRequestDto request = BookingCheckoutValidationRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                booking.getEmployee().getId(),
                booking.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(conflictingBooking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.validateHeldBookingCheckout(bookingId, request, HOLD_ACCESS_TOKEN));

        assertEquals("This slot has already been booked by someone else.", exception.getMessage());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void prepareHeldBookingCheckoutShouldCreateImmediatePaymentAndPersistCustomerDetails() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingCheckoutSessionRequestDto request = BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .confirmationTokenId("ctoken_123")
                .build();

        BookingCheckoutSessionResponseDto checkoutResponse = BookingCheckoutSessionResponseDto.builder()
                .paymentIntentId("pi_checkout")
                .clientSecret("pi_checkout_secret")
                .paymentStatus("succeeded")
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(stripePaymentService.createAndConfirmPaymentWithConfirmationTokenDetails(any(), any(), any(), any()))
                .thenReturn(paymentConfirmationResult(
                        checkoutResponse,
                        paymentIntentDetails(
                                "pi_checkout",
                                "succeeded",
                                1000L,
                                Map.of("bookingId", bookingId.toString()))));

        BookingCheckoutSessionResponseDto result = bookingService.prepareHeldBookingCheckout(
                bookingId,
                request,
                HOLD_ACCESS_TOKEN);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);

        assertEquals("John Doe", booking.getCustomerName());
        assertEquals("john@example.com", booking.getCustomerEmail());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("pi_checkout", booking.getStripePaymentIntentId());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        verify(stripePaymentService).createAndConfirmPaymentWithConfirmationTokenDetails(
                any(),
                any(),
                any(),
                metadataCaptor.capture());
        assertEquals(bookingId.toString(), metadataCaptor.getValue().get("bookingId"));
        assertEquals(booking.getEmployee().getId().toString(), metadataCaptor.getValue().get("employeeId"));
        assertEquals(booking.getTreatment().getId().toString(), metadataCaptor.getValue().get("treatmentId"));
        org.junit.jupiter.api.Assertions.assertFalse(metadataCaptor.getValue().containsKey("customerEmail"));
        assertEquals(checkoutResponse, result);
    }

    @Test
    void prepareHeldBookingCheckoutShouldUseNonPiiMetadataForSlotHoldFlow() {
        UUID slotHoldId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setActive(true);
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(ACTIVE_BOOKING_DATE);
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(10, 30));
        slotHold.setHoldAmount(new BigDecimal("35.00"));
        slotHold.setExpiresAt(ACTIVE_HOLD_EXPIRY);
        slotHold.setHoldAccessTokenHash(holdAccessTokenService.hashToken(HOLD_ACCESS_TOKEN));

        BookingCheckoutSessionRequestDto request = BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .confirmationTokenId("ctoken_123")
                .build();

        BookingCheckoutSessionResponseDto checkoutResponse = BookingCheckoutSessionResponseDto.builder()
                .paymentIntentId("pi_slot_hold")
                .clientSecret("pi_slot_hold_secret")
                .paymentStatus("succeeded")
                .build();

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(stripePaymentService.createAndConfirmPaymentWithConfirmationTokenDetails(any(), any(), any(), any()))
                .thenReturn(paymentConfirmationResult(
                        checkoutResponse,
                        paymentIntentDetails(
                                "pi_slot_hold",
                                "succeeded",
                                3500L,
                                Map.of("slotHoldId", slotHoldId.toString()))));

        BookingCheckoutSessionResponseDto result = bookingService.prepareHeldBookingCheckout(
                slotHoldId,
                request,
                HOLD_ACCESS_TOKEN);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);

        verify(stripePaymentService).createAndConfirmPaymentWithConfirmationTokenDetails(
                any(),
                any(),
                any(),
                metadataCaptor.capture());
        assertEquals(slotHoldId.toString(), metadataCaptor.getValue().get("slotHoldId"));
        assertEquals(employeeId.toString(), metadataCaptor.getValue().get("employeeId"));
        assertEquals(treatmentId.toString(), metadataCaptor.getValue().get("treatmentId"));
        org.junit.jupiter.api.Assertions.assertFalse(metadataCaptor.getValue().containsKey("customerEmail"));
        assertEquals("john@example.com", slotHold.getCustomerEmail());
        assertEquals("pi_slot_hold", slotHold.getStripePaymentIntentId());
        assertEquals("succeeded", slotHold.getStripePaymentStatus());
        assertEquals(checkoutResponse, result);
    }

    @Test
    void prepareHeldBookingCheckoutShouldReuseSucceededExistingPaymentIntent() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_existing");
        BookingCheckoutSessionRequestDto request = buildCheckoutRequest("ctoken_existing");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(stripePaymentService.getPaymentIntentDetails("pi_existing"))
                .thenReturn(paymentIntentDetails(
                        "pi_existing",
                        "succeeded",
                        3500L,
                        Map.of("slotHoldId", slotHoldId.toString())));

        BookingCheckoutSessionResponseDto result = bookingService.prepareHeldBookingCheckout(
                slotHoldId,
                request,
                HOLD_ACCESS_TOKEN);

        assertEquals("pi_existing", result.getPaymentIntentId());
        assertEquals("succeeded", result.getPaymentStatus());
        assertEquals(null, result.getClientSecret());
        assertEquals("John Doe", slotHold.getCustomerName());
        assertEquals("john@example.com", slotHold.getCustomerEmail());
        assertEquals("succeeded", slotHold.getStripePaymentStatus());
        assertNotNull(slotHold.getPaymentCapturedAt());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void prepareHeldBookingCheckoutShouldRejectExistingPaymentIntentRequiresAction() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_requires_action");
        BookingCheckoutSessionRequestDto request = buildCheckoutRequest("ctoken_requires_action");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(stripePaymentService.getPaymentIntentDetails("pi_requires_action"))
                .thenReturn(paymentIntentDetails(
                        "pi_requires_action",
                        "requires_action",
                        3500L,
                        Map.of("slotHoldId", slotHoldId.toString())));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.prepareHeldBookingCheckout(slotHoldId, request, HOLD_ACCESS_TOKEN));

        assertEquals(
                "Stripe is still processing the payment for this booking. Please finish the current checkout flow.",
                exception.getMessage());
        assertEquals("requires_action", slotHold.getStripePaymentStatus());
        assertEquals(null, slotHold.getPaymentCapturedAt());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void prepareHeldBookingCheckoutShouldRejectExistingPaymentIntentProcessing() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_processing");
        BookingCheckoutSessionRequestDto request = buildCheckoutRequest("ctoken_processing");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(stripePaymentService.getPaymentIntentDetails("pi_processing"))
                .thenReturn(paymentIntentDetails(
                        "pi_processing",
                        "processing",
                        3500L,
                        Map.of("slotHoldId", slotHoldId.toString())));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.prepareHeldBookingCheckout(slotHoldId, request, HOLD_ACCESS_TOKEN));

        assertEquals(
                "Stripe is still processing the payment for this booking. Please finish the current checkout flow.",
                exception.getMessage());
        assertEquals("processing", slotHold.getStripePaymentStatus());
        assertEquals(null, slotHold.getPaymentCapturedAt());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void prepareHeldBookingCheckoutShouldPropagateExistingPaymentIntentLookupFailure() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_lookup_failed");
        BookingCheckoutSessionRequestDto request = buildCheckoutRequest("ctoken_lookup_failed");
        PaymentProcessingException paymentException = new PaymentProcessingException(
                "Unable to verify payment status right now. Please try again.");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(stripePaymentService.getPaymentIntentDetails("pi_lookup_failed")).thenThrow(paymentException);

        PaymentProcessingException exception = assertThrows(
                PaymentProcessingException.class,
                () -> bookingService.prepareHeldBookingCheckout(slotHoldId, request, HOLD_ACCESS_TOKEN));

        assertSame(paymentException, exception);
        assertEquals("pi_lookup_failed", slotHold.getStripePaymentIntentId());
        assertEquals(null, slotHold.getStripePaymentStatus());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void prepareHeldBookingCheckoutShouldCreateNewPaymentIntentWhenExistingIntentCannotBeReused() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_old");
        BookingCheckoutSessionRequestDto request = buildCheckoutRequest("ctoken_new");
        BookingCheckoutSessionResponseDto checkoutResponse = BookingCheckoutSessionResponseDto.builder()
                .paymentIntentId("pi_new")
                .clientSecret("pi_new_secret")
                .paymentStatus("succeeded")
                .build();

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(stripePaymentService.getPaymentIntentDetails("pi_old"))
                .thenReturn(paymentIntentDetails(
                        "pi_old",
                        "requires_payment_method",
                        3500L,
                        Map.of("slotHoldId", slotHoldId.toString())));
        when(stripePaymentService.createAndConfirmPaymentWithConfirmationTokenDetails(any(), any(), any(), any()))
                .thenReturn(paymentConfirmationResult(
                        checkoutResponse,
                        paymentIntentDetails(
                                "pi_new",
                                "succeeded",
                                3500L,
                                Map.of("slotHoldId", slotHoldId.toString()))));

        BookingCheckoutSessionResponseDto result = bookingService.prepareHeldBookingCheckout(
                slotHoldId,
                request,
                HOLD_ACCESS_TOKEN);

        assertEquals(checkoutResponse, result);
        assertEquals("pi_new", slotHold.getStripePaymentIntentId());
        assertEquals("succeeded", slotHold.getStripePaymentStatus());
        assertNotNull(slotHold.getPaymentCapturedAt());
        verify(stripePaymentService).getPaymentIntentDetails("pi_old");
        verify(stripePaymentService).createAndConfirmPaymentWithConfirmationTokenDetails(
                new BigDecimal("35.00"),
                "john@example.com",
                "ctoken_new",
                Map.of(
                        "slotHoldId", slotHoldId.toString(),
                        "employeeId", slotHold.getEmployee().getId().toString(),
                        "treatmentId", slotHold.getTreatment().getId().toString(),
                        "bookingDate", slotHold.getBookingDate().toString(),
                        "startTime", slotHold.getStartTime().toString(),
                        "endTime", slotHold.getEndTime().toString()));
    }

    @Test
    void prepareHeldBookingCheckoutShouldRejectBlacklistedCustomer() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingCheckoutSessionRequestDto request = BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("Blocked Customer")
                        .email("blocked@example.com")
                        .phone("+353870000000")
                        .build())
                .confirmationTokenId("ctoken_blocked")
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingBlacklistService.isBlockedCustomer("blocked@example.com", "+353870000000")).thenReturn(true);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.prepareHeldBookingCheckout(bookingId, request, HOLD_ACCESS_TOKEN));

        assertEquals(
                "Booking through the website is temporarily unavailable. Please contact the barbershop directly.",
                exception.getMessage());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void prepareHeldBookingCheckoutShouldRejectWrongHoldAccessToken() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        BookingCheckoutSessionRequestDto request = buildCheckoutRequest("ctoken_wrong_owner");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.prepareHeldBookingCheckout(slotHoldId, request, "wrong-token"));

        assertEquals("This appointment hold is no longer available.", exception.getMessage());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
        verify(slotHoldRepository, never()).save(slotHold);
    }

    @Test
    void holdSlotShouldRejectWhenSameIpAlreadyHasTwoActiveHolds() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        when(bookingRepository.countActiveUnpaidHoldsByClientIp(
                eq("203.0.113.10"),
                eq(BookingStatus.PENDING),
                any(LocalDateTime.class))).thenReturn(2L);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals(
                "This connection already has two active appointment holds. Please complete or release an existing hold before selecting another slot.",
                exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void holdSlotShouldRejectWhenSameDeviceAlreadyHasTwoActiveHolds() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        when(bookingRepository.countActiveUnpaidHoldsByClientIp(
                eq("203.0.113.10"),
                eq(BookingStatus.PENDING),
                any(LocalDateTime.class))).thenReturn(1L);
        when(bookingRepository.countActiveUnpaidHoldsByClientDeviceId(
                eq("device-123"),
                eq(BookingStatus.PENDING),
                any(LocalDateTime.class))).thenReturn(2L);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals(
                "This device already has two active appointment holds. Please complete or release an existing hold before selecting another slot.",
                exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void holdSlotShouldRejectWhenSameSlotAlreadyReachedGlobalHoldLimit() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        when(bookingRepository.countActiveUnpaidHoldsForSlot(
                eq(employeeId),
                eq(request.getBookingDate()),
                eq(request.getStartTime()),
                eq(request.getEndTime()),
                eq(BookingStatus.PENDING),
                any(LocalDateTime.class))).thenReturn(1L);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals("This time slot is currently unavailable. Please select a different available time.",
                exception.getMessage());
        verify(employeeRepository, never()).findByIdAndActiveTrueForUpdate(any(UUID.class));
        verify(slotHoldRepository, never()).save(any());
    }

    @Test
    void confirmHeldBookingShouldRejectMissingHoldAccessTokenBeforeStripeLookup() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_slot_confirm");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.confirmHeldBooking(slotHoldId, BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_slot_confirm")
                        .build(), null));

        assertEquals("This appointment hold is no longer available.", exception.getMessage());
        verify(stripePaymentService, never()).getPaymentIntentDetails(anyString());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void confirmHeldBookingShouldRejectWrongHoldAccessTokenBeforeStripeLookup() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_slot_confirm");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.confirmHeldBooking(slotHoldId, BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_slot_confirm")
                        .build(), "wrong-token"));

        assertEquals("This appointment hold is no longer available.", exception.getMessage());
        verify(stripePaymentService, never()).getPaymentIntentDetails(anyString());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void confirmHeldBookingShouldRejectMismatchedPaymentIntentBeforeStripeLookup() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_persisted");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.confirmHeldBooking(slotHoldId, BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_request")
                        .build(), HOLD_ACCESS_TOKEN));

        assertEquals("This Stripe payment does not match the current appointment hold.", exception.getMessage());
        verify(stripePaymentService, never()).getPaymentIntentDetails(anyString());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void confirmHeldBookingShouldCallStripeAndConfirmWhenTokenAndPersistedPaymentIntentAreValid() {
        UUID slotHoldId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setCustomerName("John Doe");
        slotHold.setCustomerEmail("john@example.com");
        slotHold.setCustomerPhone("+353870000000");
        slotHold.setStripePaymentIntentId("pi_slot_confirm");

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(stripePaymentService.getPaymentIntentDetails("pi_slot_confirm"))
                .thenReturn(paymentIntentDetails(
                        "pi_slot_confirm",
                        "succeeded",
                        3500L,
                        Map.of("slotHoldId", slotHoldId.toString())));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });
        when(mapper.toDto(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            return BookingResponseDto.builder()
                    .id(entity.getId())
                    .status(entity.getStatus())
                    .stripePaymentIntentId(entity.getStripePaymentIntentId())
                    .build();
        });

        BookingResponseDto result = bookingService.confirmHeldBooking(slotHoldId,
                BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_slot_confirm")
                        .build(),
                HOLD_ACCESS_TOKEN);

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals("pi_slot_confirm", result.getStripePaymentIntentId());
        InOrder inOrder = inOrder(slotHoldRepository, stripePaymentService);
        inOrder.verify(slotHoldRepository).findByIdForUpdate(slotHoldId);
        inOrder.verify(stripePaymentService).getPaymentIntentDetails("pi_slot_confirm");
        verify(slotHoldRepository).delete(slotHold);
    }

    @Test
    void confirmHeldBookingShouldConfirmSucceededPaymentImmediately() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentIntentId("pi_confirm");

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(stripePaymentService.getPaymentIntentDetails("pi_confirm"))
                .thenReturn(paymentIntentDetails(
                        "pi_confirm",
                        "succeeded",
                        1000L,
                        Map.of("bookingId", bookingId.toString())));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .build());

        BookingResponseDto result = bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                .paymentIntentId("pi_confirm")
                .build(), HOLD_ACCESS_TOKEN);

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void confirmHeldBookingShouldRejectMismatchedPaymentAmount() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentIntentId("pi_confirm_amount_mismatch");

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(stripePaymentService.getPaymentIntentDetails("pi_confirm_amount_mismatch"))
                .thenReturn(paymentIntentDetails(
                        "pi_confirm_amount_mismatch",
                        "succeeded",
                        999L,
                        Map.of("bookingId", bookingId.toString())));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_confirm_amount_mismatch")
                        .build(), HOLD_ACCESS_TOKEN));

        assertEquals("This Stripe payment does not match the current appointment hold.", exception.getMessage());
        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals(null, booking.getPaymentCapturedAt());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void confirmHeldBookingShouldConfirmEvenIfHoldJustExpiredAfterStripeSucceeded() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentIntentId("pi_expired_but_paid");
        booking.setExpiresAt(EXPIRED_HOLD_EXPIRY);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(stripePaymentService.getPaymentIntentDetails("pi_expired_but_paid"))
                .thenReturn(paymentIntentDetails(
                        "pi_expired_but_paid",
                        "succeeded",
                        1000L,
                        Map.of("bookingId", bookingId.toString())));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .build());

        BookingResponseDto result = bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                .paymentIntentId("pi_expired_but_paid")
                .build(), HOLD_ACCESS_TOKEN);

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void confirmHeldBookingShouldExpireElapsedHold() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentIntentId("pi_expired");
        booking.setExpiresAt(EXPIRED_HOLD_EXPIRY);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(stripePaymentService.getPaymentIntentDetails("pi_expired"))
                .thenReturn(paymentIntentDetails(
                        "pi_expired",
                        "requires_payment_method",
                        1000L,
                        Map.of("bookingId", bookingId.toString())));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_expired")
                        .build(), HOLD_ACCESS_TOKEN));

        assertEquals("This appointment hold has expired. Please choose another time.", exception.getMessage());
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    void confirmHeldBookingShouldRejectWhenSlotWasTakenByDoneBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingEntity conflictingBooking = buildConflictingBooking(booking, BookingStatus.DONE);
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentIntentId("pi_slot_taken");

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(stripePaymentService.getPaymentIntentDetails("pi_slot_taken"))
                .thenReturn(paymentIntentDetails(
                        "pi_slot_taken",
                        "succeeded",
                        1000L,
                        Map.of("bookingId", bookingId.toString())));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                booking.getEmployee().getId(),
                booking.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(conflictingBooking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_slot_taken")
                        .build(), HOLD_ACCESS_TOKEN));

        assertEquals("This slot has already been booked by someone else.", exception.getMessage());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void createAdminBookingShouldRejectInvertedSlot() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(ACTIVE_BOOKING_DATE)
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(13, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .build();

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.createAdminBooking(request));

        assertEquals("End time must be after start time.", exception.getMessage());
        verify(employeeRepository, never()).findByIdAndActiveTrueForUpdate(any(UUID.class));
        verify(availabilityService, never()).validateSlotSelection(
                any(UUID.class),
                any(UUID.class),
                any(LocalDate.class),
                any(LocalTime.class),
                any(LocalTime.class));
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void createAdminBookingShouldRejectBlacklistedPhone() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(ACTIVE_BOOKING_DATE)
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .build();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        org.mockito.Mockito
                .doThrow(new BookingValidationException(
                        "This phone number is in the booking blacklist and cannot be used for appointments."))
                .when(bookingBlacklistService)
                .validateAllowedCustomer(null, "+353831234567");

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.createAdminBooking(request));

        assertEquals(
                "This phone number is in the booking blacklist and cannot be used for appointments.",
                exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createAdminBookingShouldRejectUnsupportedEmployeeTreatmentPair() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(ACTIVE_BOOKING_DATE)
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .build();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        org.mockito.Mockito
                .doThrow(new BookingValidationException("This employee does not provide the selected service."))
                .when(availabilityService)
                .validateSlotSelection(
                        employeeId,
                        treatmentId,
                        request.getBookingDate(),
                        request.getStartTime(),
                        request.getEndTime());

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.createAdminBooking(request));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void publicCancelShouldCancelUnpaidPendingHold() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(bookingId, HOLD_ACCESS_TOKEN);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(null, booking.getExpiresAt());
    }

    @Test
    void publicCancelShouldRejectWrongHoldAccessToken() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.cancelBooking(slotHoldId, "wrong-token"));

        assertEquals("This appointment hold is no longer available.", exception.getMessage());
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
        verify(bookingRepository, never()).findById(slotHoldId);
    }

    @Test
    void publicCancelShouldReleaseUnpaidPublicSlotHold() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));

        bookingService.cancelBooking(slotHoldId, HOLD_ACCESS_TOKEN);

        verify(slotHoldRepository).delete(slotHold);
        verify(bookingRepository, never()).findById(slotHoldId);
    }

    @Test
    void publicCancelShouldRejectPaidPendingBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentStatus("succeeded");
        booking.setPaymentCapturedAt(PAST_PAYMENT_TIMESTAMP);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.cancelBooking(bookingId, HOLD_ACCESS_TOKEN));

        assertEquals("Paid bookings can only be updated from the admin panel.", exception.getMessage());
    }

    @Test
    void publicCancelShouldRejectPaidPublicSlotHold() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentStatus("succeeded");
        slotHold.setPaymentCapturedAt(PAST_PAYMENT_TIMESTAMP);

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.cancelBooking(slotHoldId, HOLD_ACCESS_TOKEN));

        assertEquals("Paid bookings can only be updated from the admin panel.", exception.getMessage());
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
        verify(bookingRepository, never()).findById(slotHoldId);
    }

    @Test
    void cancelBookingByAdminShouldCancelPendingBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentStatus("succeeded");
        booking.setPaymentCapturedAt(PAST_PAYMENT_TIMESTAMP);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CANCELLED)
                .build());

        BookingResponseDto result = bookingService.cancelBookingByAdmin(bookingId);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(Boolean.TRUE, booking.getSlotLocked());
        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "ADMIN_BOOKING_CANCEL".equals(event.getEventType())
                        && "PENDING".equals(event.getAdditionalFields().get("oldStatus"))
                        && "CANCELLED".equals(event.getAdditionalFields().get("newStatus"))));
    }

    @Test
    void updateBookingByAdminShouldRejectZeroLengthSlot() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(ACTIVE_BOOKING_DATE)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 0))
                .customerName("Jane Doe")
                .customerPhone("+353871111111")
                .customerEmail("jane@example.com")
                .holdAmount(new BigDecimal("45.00"))
                .status(BookingStatus.EXPIRED)
                .build();

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.updateBookingByAdmin(bookingId, request));

        assertEquals("End time must be after start time.", exception.getMessage());
        verify(bookingRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(availabilityService, never()).validateSlotSelectionExcludingBooking(
                any(UUID.class),
                any(UUID.class),
                any(LocalDate.class),
                any(LocalTime.class),
                any(LocalTime.class),
                any(UUID.class));
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void updateBookingByAdminShouldPersistEditableBookingFields() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);

        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSlotLocked(false);
        booking.setCustomerName("John Doe");
        booking.setCustomerPhone("+353870000000");
        booking.setCustomerEmail("john@example.com");
        booking.getEmployee().setId(employeeId);
        booking.getTreatment().setId(treatmentId);

        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(booking.getBookingDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .customerName("Jane Doe")
                .customerPhone("+353871111111")
                .customerEmail("jane@example.com")
                .holdAmount(new BigDecimal("45.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatmentId)).thenReturn(Optional.of(treatment));
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("jane@example.com", "+353871111111");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CANCELLED)
                .customerName("Jane Doe")
                .customerPhone("+353871111111")
                .customerEmail("jane@example.com")
                .holdAmount(new BigDecimal("45.00"))
                .build());

        BookingResponseDto result = bookingService.updateBookingByAdmin(bookingId, request);

        assertEquals("Jane Doe", booking.getCustomerName());
        assertEquals("+353871111111", booking.getCustomerPhone());
        assertEquals("jane@example.com", booking.getCustomerEmail());
        assertEquals(new BigDecimal("45.00"), booking.getHoldAmount());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(Boolean.TRUE, booking.getSlotLocked());
        verify(availabilityService, never()).validateSlotSelectionExcludingBooking(any(), any(), any(), any(), any(),
                any());
        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        verify(securityAuditLogger)
                .log(org.mockito.ArgumentMatchers.argThat(event -> "ADMIN_BOOKING_UPDATE".equals(event.getEventType())
                        && bookingId.toString().equals(event.getResourceId())
                        && "CONFIRMED".equals(event.getAdditionalFields().get("oldStatus"))
                        && "CANCELLED".equals(event.getAdditionalFields().get("newStatus"))));
    }

    @Test
    void updateBookingByAdminShouldRejectHoldAmountChangeAfterPaymentCaptured() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentCapturedAt(PAST_PAYMENT_TIMESTAMP);

        AdminBookingUpdateRequestDto request = buildAdminUpdateRequest(
                booking,
                new BigDecimal("12.00"),
                BookingStatus.CONFIRMED);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.updateBookingByAdmin(bookingId, request));

        assertEquals("Payment amount cannot be changed after payment has been captured.", exception.getMessage());
        assertEquals(BigDecimal.valueOf(10), booking.getHoldAmount());
        verify(employeeRepository, never()).findByIdAndActiveTrueForUpdate(any(UUID.class));
        verify(treatmentRepository, never()).findByIdAndActiveTrue(any(UUID.class));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void updateBookingByAdminShouldRejectHoldAmountChangeAfterStripeSucceeded() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setStripePaymentStatus("succeeded");

        AdminBookingUpdateRequestDto request = buildAdminUpdateRequest(
                booking,
                new BigDecimal("12.00"),
                BookingStatus.CONFIRMED);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.updateBookingByAdmin(bookingId, request));

        assertEquals("Payment amount cannot be changed after payment has been captured.", exception.getMessage());
        assertEquals(BigDecimal.valueOf(10), booking.getHoldAmount());
        verify(employeeRepository, never()).findByIdAndActiveTrueForUpdate(any(UUID.class));
        verify(treatmentRepository, never()).findByIdAndActiveTrue(any(UUID.class));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void updateBookingByAdminShouldAllowNonFinancialFieldsAfterPayment() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCustomerName("John Doe");
        booking.setCustomerPhone("+353870000000");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentStatus("succeeded");
        LocalDateTime capturedAt = PAST_PAYMENT_TIMESTAMP;
        booking.setPaymentCapturedAt(capturedAt);

        EmployeeEntity employee = booking.getEmployee();
        TreatmentEntity treatment = booking.getTreatment();

        AdminBookingUpdateRequestDto request = buildAdminUpdateRequest(
                booking,
                new BigDecimal("10.00"),
                BookingStatus.CONFIRMED);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employee.getId())).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatment.getId())).thenReturn(Optional.of(treatment));
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("jane@example.com", "+353871111111");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .customerName("Jane Doe")
                .customerPhone("+353871111111")
                .customerEmail("jane@example.com")
                .holdAmount(BigDecimal.valueOf(10))
                .build());

        BookingResponseDto result = bookingService.updateBookingByAdmin(bookingId, request);

        assertEquals("Jane Doe", booking.getCustomerName());
        assertEquals("+353871111111", booking.getCustomerPhone());
        assertEquals("jane@example.com", booking.getCustomerEmail());
        assertEquals(BigDecimal.valueOf(10), booking.getHoldAmount());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertSame(capturedAt, booking.getPaymentCapturedAt());
        verify(availabilityService, never()).validateSlotSelectionExcludingBooking(any(), any(), any(), any(), any(),
                any());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void updateBookingByAdminShouldAllowHoldAmountChangeBeforePayment() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);

        EmployeeEntity employee = booking.getEmployee();
        TreatmentEntity treatment = booking.getTreatment();

        AdminBookingUpdateRequestDto request = buildAdminUpdateRequest(
                booking,
                new BigDecimal("12.00"),
                BookingStatus.CONFIRMED);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employee.getId())).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatment.getId())).thenReturn(Optional.of(treatment));
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("jane@example.com", "+353871111111");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .holdAmount(new BigDecimal("12.00"))
                .build());

        BookingResponseDto result = bookingService.updateBookingByAdmin(bookingId, request);

        assertEquals(new BigDecimal("12.00"), booking.getHoldAmount());
        assertEquals(new BigDecimal("12.00"), result.getHoldAmount());
    }

    @Test
    void updateBookingByAdminShouldValidateMovedSlotExcludingCurrentBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);

        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCustomerName("John Doe");

        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(booking.getBookingDate().plusDays(1))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .customerName("John Doe")
                .customerPhone("+353870000000")
                .customerEmail("john@example.com")
                .holdAmount(new BigDecimal("10.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatmentId)).thenReturn(Optional.of(treatment));
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("john@example.com", "+353870000000");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingService.updateBookingByAdmin(bookingId, request);

        verify(availabilityService).validateSlotSelectionExcludingBooking(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime(),
                bookingId);
    }

    @Test
    void updateBookingByAdminShouldValidateMovedDoneBookingSlotExcludingCurrentBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);

        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCustomerName("John Doe");

        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(booking.getBookingDate().plusDays(1))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .customerName("John Doe")
                .customerPhone("+353870000000")
                .customerEmail("john@example.com")
                .holdAmount(new BigDecimal("10.00"))
                .status(BookingStatus.DONE)
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatmentId)).thenReturn(Optional.of(treatment));
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("john@example.com", "+353870000000");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.DONE)
                .build());

        bookingService.updateBookingByAdmin(bookingId, request);

        verify(availabilityService).validateSlotSelectionExcludingBooking(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime(),
                bookingId);
    }

    @Test
    void updateBookingByAdminShouldRejectUnsupportedEmployeeTreatmentPair() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);

        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);

        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(booking.getBookingDate().plusDays(1))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .customerName("John Doe")
                .customerPhone("+353870000000")
                .customerEmail("john@example.com")
                .holdAmount(new BigDecimal("10.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatmentId)).thenReturn(Optional.of(treatment));
        org.mockito.Mockito
                .doThrow(new BookingValidationException("This employee does not provide the selected service."))
                .when(availabilityService)
                .validateSlotSelectionExcludingBooking(
                        employeeId,
                        treatmentId,
                        request.getBookingDate(),
                        request.getStartTime(),
                        request.getEndTime(),
                        bookingId);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.updateBookingByAdmin(bookingId, request));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldConfirmPendingBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_confirmed");
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_confirmed")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_confirmed",
                        "succeeded",
                        1000L,
                        Map.of("bookingId", bookingId.toString())),
                "payment_intent.succeeded");

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldKeepConfirmedBookingStableOnRepeatedSuccess() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_repeated");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setStripePaymentStatus("succeeded");
        LocalDateTime capturedAt = PAST_PAYMENT_TIMESTAMP;
        booking.setPaymentCapturedAt(capturedAt);

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_repeated")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_repeated",
                        "succeeded",
                        1000L,
                        Map.of("bookingId", bookingId.toString())),
                "payment_intent.succeeded");

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertSame(capturedAt, booking.getPaymentCapturedAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldConfirmExpiredBookingAfterSuccessfulPayment() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_late_webhook");
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setExpiresAt(null);

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_late_webhook"))
                .thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_late_webhook",
                        "succeeded",
                        1000L,
                        Map.of("bookingId", bookingId.toString())),
                "payment_intent.succeeded");

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldStoreReleaseTimestampForFailedPayment() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_failed");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_failed")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_failed",
                "payment_failed",
                "payment_intent.payment_failed");

        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals("payment_failed", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentReleasedAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldStoreReleaseTimestampForFailedSlotHoldPayment() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_slot_failed");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_slot_failed")).thenReturn(Optional.empty());
        when(slotHoldRepository.findByStripePaymentIntentIdForUpdate("pi_slot_failed"))
                .thenReturn(Optional.of(slotHold));

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_slot_failed",
                "payment_failed",
                "payment_intent.payment_failed");

        assertEquals(Boolean.TRUE, slotHold.getActive());
        assertEquals("payment_failed", slotHold.getStripePaymentStatus());
        assertNotNull(slotHold.getPaymentReleasedAt());
        verify(slotHoldRepository).save(slotHold);
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldStoreReleaseTimestampForCanceledSlotHoldPayment() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_slot_canceled");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_slot_canceled")).thenReturn(Optional.empty());
        when(slotHoldRepository.findByStripePaymentIntentIdForUpdate("pi_slot_canceled"))
                .thenReturn(Optional.of(slotHold));

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_slot_canceled",
                "canceled",
                "payment_intent.canceled");

        assertEquals(Boolean.TRUE, slotHold.getActive());
        assertEquals("canceled", slotHold.getStripePaymentStatus());
        assertNotNull(slotHold.getPaymentReleasedAt());
        verify(slotHoldRepository).save(slotHold);
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldRecoverBookingUsingMetadataWhenPaymentIntentWasNotPersisted() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_booking_metadata"))
                .thenReturn(Optional.empty());
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_booking_metadata",
                        "succeeded",
                        1000L,
                        Map.of("bookingId", bookingId.toString())),
                "payment_intent.succeeded");

        assertEquals("pi_booking_metadata", booking.getStripePaymentIntentId());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        verify(bookingRepository).save(booking);
        verify(slotHoldRepository, never()).findByStripePaymentIntentIdForUpdate("pi_booking_metadata");
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldIgnoreInvalidBookingMetadataUuid() {
        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_invalid_metadata"))
                .thenReturn(Optional.empty());
        when(slotHoldRepository.findByStripePaymentIntentIdForUpdate("pi_invalid_metadata"))
                .thenReturn(Optional.empty());

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_invalid_metadata",
                "succeeded",
                "payment_intent.succeeded",
                Map.of("bookingId", "not-a-uuid"));

        verify(bookingRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(bookingRepository, never()).save(any(BookingEntity.class));
        verify(slotHoldRepository).findByStripePaymentIntentIdForUpdate("pi_invalid_metadata");
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldUsePaymentIntentLookupWhenMetadataIsMissing() {
        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_missing_metadata"))
                .thenReturn(Optional.empty());
        when(slotHoldRepository.findByStripePaymentIntentIdForUpdate("pi_missing_metadata"))
                .thenReturn(Optional.empty());

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_missing_metadata",
                "succeeded",
                "payment_intent.succeeded",
                Map.of());

        verify(bookingRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(slotHoldRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(slotHoldRepository).findByStripePaymentIntentIdForUpdate("pi_missing_metadata");
        verify(bookingRepository, never()).save(any(BookingEntity.class));
        verify(slotHoldRepository, never()).save(any(SlotHoldEntity.class));
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldRejectMetadataRecoveredBookingPaymentIntentMismatch() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_existing");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_incoming")).thenReturn(Optional.empty());
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_incoming",
                "succeeded",
                "payment_intent.succeeded",
                Map.of("bookingId", bookingId.toString()));

        assertEquals("pi_existing", booking.getStripePaymentIntentId());
        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals(null, booking.getStripePaymentStatus());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
        verify(slotHoldRepository, never()).findByStripePaymentIntentIdForUpdate("pi_incoming");
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldRecoverSlotHoldUsingMetadataWhenPaymentIntentWasNotPersisted() {
        UUID slotHoldId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setActive(true);
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setCustomerName("John Doe");
        slotHold.setCustomerEmail("john@example.com");
        slotHold.setCustomerPhone("+353870000000");
        slotHold.setBookingDate(ACTIVE_BOOKING_DATE);
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(10, 30));
        slotHold.setExpiresAt(ACTIVE_HOLD_EXPIRY);
        slotHold.setHoldAmount(new BigDecimal("35.00"));

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_metadata")).thenReturn(Optional.empty());
        when(slotHoldRepository.findByStripePaymentIntentIdForUpdate("pi_metadata")).thenReturn(Optional.empty());
        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        bookingService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_metadata",
                        "succeeded",
                        3500L,
                        Map.of("slotHoldId", slotHoldId.toString())),
                "payment_intent.succeeded");

        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        verify(slotHoldRepository).delete(slotHold);

        BookingEntity persistedBooking = bookingCaptor.getValue();
        assertEquals(bookingId, persistedBooking.getId());
        assertEquals(BookingStatus.CONFIRMED, persistedBooking.getStatus());
        assertEquals("John Doe", persistedBooking.getCustomerName());
        assertEquals("john@example.com", persistedBooking.getCustomerEmail());
        assertEquals("+353870000000", persistedBooking.getCustomerPhone());
        assertEquals(slotHold.getBookingDate(), persistedBooking.getBookingDate());
        assertEquals(slotHold.getStartTime(), persistedBooking.getStartTime());
        assertEquals(slotHold.getEndTime(), persistedBooking.getEndTime());
        assertEquals(new BigDecimal("35.00"), persistedBooking.getHoldAmount());
        assertEquals("pi_metadata", persistedBooking.getStripePaymentIntentId());
        assertEquals("succeeded", persistedBooking.getStripePaymentStatus());
        assertNotNull(persistedBooking.getPaymentCapturedAt());
        assertEquals("pi_metadata", slotHold.getStripePaymentIntentId());
    }

    private LocalDateTime refreshableHoldExpiry() {
        return LocalDateTime.now(ZoneId.of(TEST_TIMEZONE)).plusMinutes(1);
    }

    private StripePaymentConfirmationResult paymentConfirmationResult(
            BookingCheckoutSessionResponseDto checkoutResponse,
            StripePaymentIntentDetails paymentIntent) {
        return new StripePaymentConfirmationResult(checkoutResponse, paymentIntent);
    }

    private StripePaymentIntentDetails paymentIntentDetails(
            String paymentIntentId,
            String status,
            Long amount,
            Map<String, String> metadata) {
        return new StripePaymentIntentDetails(paymentIntentId, status, amount, "eur", metadata);
    }

    private BookingEntity buildPendingBooking(UUID bookingId) {
        EmployeeEntity employee = buildActiveBookableEmployee(UUID.randomUUID());

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(UUID.randomUUID());

        BookingEntity booking = new BookingEntity();
        booking.setId(bookingId);
        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingDate(ACTIVE_BOOKING_DATE);
        booking.setStartTime(LocalTime.of(10, 0));
        booking.setEndTime(LocalTime.of(11, 0));
        booking.setExpiresAt(ACTIVE_HOLD_EXPIRY);
        booking.setHoldAmount(BigDecimal.valueOf(10));
        booking.setSlotLocked(false);
        booking.setHoldAccessTokenHash(holdAccessTokenService.hashToken(HOLD_ACCESS_TOKEN));
        return booking;
    }

    private AdminBookingUpdateRequestDto buildAdminUpdateRequest(
            BookingEntity booking,
            BigDecimal holdAmount,
            BookingStatus status) {
        return AdminBookingUpdateRequestDto.builder()
                .employeeId(booking.getEmployee().getId())
                .treatmentId(booking.getTreatment().getId())
                .bookingDate(booking.getBookingDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .customerName("Jane Doe")
                .customerPhone("+353871111111")
                .customerEmail("jane@example.com")
                .holdAmount(holdAmount)
                .status(status)
                .build();
    }

    private BookingEntity buildConflictingBooking(BookingEntity booking, BookingStatus status) {
        BookingEntity conflictingBooking = new BookingEntity();
        conflictingBooking.setId(UUID.randomUUID());
        conflictingBooking.setEmployee(booking.getEmployee());
        conflictingBooking.setTreatment(booking.getTreatment());
        conflictingBooking.setBookingDate(booking.getBookingDate());
        conflictingBooking.setStartTime(booking.getStartTime());
        conflictingBooking.setEndTime(booking.getEndTime());
        conflictingBooking.setStatus(status);
        conflictingBooking.setSlotLocked(false);
        return conflictingBooking;
    }

    private EmployeeEntity buildActiveBookableEmployee(UUID employeeId) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setActive(true);
        employee.setBookable(true);
        return employee;
    }

    private BookingRequestDto buildRequest(UUID employeeId, UUID treatmentId) {
        return BookingRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(ACTIVE_BOOKING_DATE)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .paymentMethodId("pm_card_visa")
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .build();
    }

    private BookingCheckoutSessionRequestDto buildCheckoutRequest(String confirmationTokenId) {
        return BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .confirmationTokenId(confirmationTokenId)
                .build();
    }

    private BookingHoldRequestDto buildHoldRequest(UUID employeeId, UUID treatmentId) {
        return BookingHoldRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(ACTIVE_BOOKING_DATE)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();
    }

    private SlotHoldEntity buildPublicSlotHold(UUID slotHoldId) {
        EmployeeEntity employee = buildActiveBookableEmployee(UUID.randomUUID());
        employee.setName("Jacob");

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(UUID.randomUUID());
        treatment.setName("Haircut");
        treatment.setPrice(new BigDecimal("35.00"));

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setActive(true);
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(ACTIVE_BOOKING_DATE);
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(11, 0));
        slotHold.setHoldAmount(new BigDecimal("35.00"));
        slotHold.setExpiresAt(ACTIVE_HOLD_EXPIRY);
        slotHold.setHoldClientIp("203.0.113.10");
        slotHold.setHoldClientDeviceId("device-123");
        slotHold.setHoldAccessTokenHash(holdAccessTokenService.hashToken(HOLD_ACCESS_TOKEN));
        return slotHold;
    }

    private SlotHoldEntity buildAdminSlotHold(UUID slotHoldId, String adminHoldSessionId) {
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setHoldScope(SlotHoldScope.ADMIN);
        slotHold.setHoldClientIp("admin-panel");
        slotHold.setHoldClientDeviceId("admin-panel:" + adminHoldSessionId);
        slotHold.setHoldAccessTokenHash(null);
        return slotHold;
    }
}
