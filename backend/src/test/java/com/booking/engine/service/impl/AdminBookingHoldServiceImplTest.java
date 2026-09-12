package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminBookingHoldServiceImplTest {

    private static final String TEST_TIMEZONE = "Europe/Dublin";
    private static final LocalDate ACTIVE_BOOKING_DATE = LocalDate.of(2099, 1, 15);
    private static final LocalDateTime ACTIVE_HOLD_EXPIRY = LocalDateTime.of(2099, 1, 15, 12, 0);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotHoldRepository slotHoldRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private BookingMapper mapper;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private BookingBlacklistService bookingBlacklistService;

    private AdminBookingHoldServiceImpl adminBookingHoldService;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone(TEST_TIMEZONE);

        BookingStateMachine bookingStateMachine = new BookingStateMachineImpl(
                bookingRepository,
                slotHoldRepository,
                bookingProperties);
        BookingValidator bookingValidator = new BookingValidatorImpl(
                bookingRepository,
                slotHoldRepository,
                bookingBlacklistService,
                bookingStateMachine,
                new BookingHoldAccessTokenService(),
                bookingProperties);
        adminBookingHoldService = new AdminBookingHoldServiceImpl(
                bookingRepository,
                slotHoldRepository,
                employeeRepository,
                treatmentRepository,
                mapper,
                new BookingHoldResponseMapper(),
                availabilityService,
                bookingBlacklistService,
                bookingValidator,
                bookingStateMachine,
                bookingProperties);

        org.mockito.Mockito.lenient().when(slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                any(UUID.class),
                any(LocalDate.class),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                any(UUID.class),
                any(LocalDate.class),
                any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(slotHoldRepository.save(any(SlotHoldEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void holdAdminSlotShouldReleasePreviousSessionHoldAndSaveNewHold() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID savedHoldId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = buildTreatment(treatmentId, new BigDecimal("35.00"));

        SlotHoldEntity existingHold = buildAdminSlotHold(UUID.randomUUID(), "session-123");

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
            entity.setId(savedHoldId);
            return entity;
        });

        BookingResponseDto result = adminBookingHoldService.holdAdminSlot(request, "session-123");

        assertEquals(savedHoldId, result.getId());
        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertNotNull(result.getExpiresAt());
        verify(slotHoldRepository).delete(existingHold);

        ArgumentCaptor<SlotHoldEntity> holdCaptor = ArgumentCaptor.forClass(SlotHoldEntity.class);
        verify(slotHoldRepository).save(holdCaptor.capture());
        SlotHoldEntity savedHold = holdCaptor.getValue();
        assertEquals(Boolean.TRUE, savedHold.getActive());
        assertEquals(SlotHoldScope.ADMIN, savedHold.getHoldScope());
        assertEquals("admin-panel", savedHold.getHoldClientIp());
        assertEquals("admin-panel:session-123", savedHold.getHoldClientDeviceId());
        assertEquals(null, savedHold.getHoldAccessTokenHash());
    }

    @Test
    void refreshAdminHoldShouldExtendMatchingSlotHold() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildAdminSlotHold(slotHoldId, "session-123");
        LocalDateTime previousExpiry = refreshableHoldExpiry();
        slotHold.setExpiresAt(previousExpiry);

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(slotHoldRepository.save(slotHold)).thenReturn(slotHold);

        BookingResponseDto result = adminBookingHoldService.refreshAdminHold(slotHoldId, "session-123");

        assertEquals(slotHoldId, result.getId());
        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertNotNull(slotHold.getExpiresAt());
        org.junit.jupiter.api.Assertions.assertTrue(slotHold.getExpiresAt().isAfter(previousExpiry));
        verify(slotHoldRepository).save(slotHold);
    }

    @Test
    void confirmAdminHeldBookingShouldCreateBookingAndReleaseSlotHold() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID slotHoldId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(ACTIVE_BOOKING_DATE)
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .holdBookingId(slotHoldId)
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .customerEmail("phone@example.com")
                .build();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = buildTreatment(treatmentId, new BigDecimal("28.00"));
        SlotHoldEntity slotHold = buildAdminSlotHold(slotHoldId, "session-123");
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(request.getBookingDate());
        slotHold.setStartTime(request.getStartTime());
        slotHold.setEndTime(request.getEndTime());

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("phone@example.com", "+353831234567");
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        BookingEntity result = adminBookingHoldService.confirmAdminHeldBooking(
                request,
                "session-123",
                "phone@example.com");

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals("Phone Client", result.getCustomerName());
        assertEquals("+353831234567", result.getCustomerPhone());
        assertEquals("phone@example.com", result.getCustomerEmail());
        assertEquals(new BigDecimal("28.00"), result.getHoldAmount());
        verify(slotHoldRepository).delete(slotHold);
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

    private SlotHoldEntity buildAdminSlotHold(UUID slotHoldId, String adminHoldSessionId) {
        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setActive(true);
        slotHold.setHoldScope(SlotHoldScope.ADMIN);
        slotHold.setHoldClientIp("admin-panel");
        slotHold.setHoldClientDeviceId("admin-panel:" + adminHoldSessionId);
        slotHold.setEmployee(buildActiveBookableEmployee(UUID.randomUUID()));
        slotHold.setTreatment(buildTreatment(UUID.randomUUID(), new BigDecimal("35.00")));
        slotHold.setBookingDate(ACTIVE_BOOKING_DATE);
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(11, 0));
        slotHold.setHoldAmount(new BigDecimal("35.00"));
        slotHold.setExpiresAt(ACTIVE_HOLD_EXPIRY);
        return slotHold;
    }

    private LocalDateTime refreshableHoldExpiry() {
        return LocalDateTime.now(ZoneId.of(TEST_TIMEZONE)).plusMinutes(1);
    }

    private TreatmentEntity buildTreatment(UUID treatmentId, BigDecimal price) {
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(price);
        return treatment;
    }

    private EmployeeEntity buildActiveBookableEmployee(UUID employeeId) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setActive(true);
        employee.setBookable(true);
        return employee;
    }
}
