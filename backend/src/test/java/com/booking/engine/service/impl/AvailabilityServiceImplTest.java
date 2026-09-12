package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.BookingValidator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AvailabilityServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceImplTest {

    private static final LocalDate ACTIVE_BOOKING_DATE = LocalDate.of(2099, 1, 15);
    private static final LocalDate SECOND_ACTIVE_BOOKING_DATE = LocalDate.of(2099, 1, 16);
    private static final LocalDate PAST_BOOKING_DATE = LocalDate.of(2000, 1, 15);
    private static final LocalDateTime ACTIVE_HOLD_EXPIRY = LocalDateTime.of(2099, 1, 15, 12, 0);
    private static final LocalDateTime EXPIRED_HOLD_EXPIRY = LocalDateTime.of(2000, 1, 15, 12, 0);
    private static final LocalDateTime PAYMENT_CAPTURED_AT = LocalDateTime.of(2020, 1, 15, 12, 0);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotHoldRepository slotHoldRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeDailyScheduleRepository employeeScheduleRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private BookingProperties bookingProperties;

    @Mock
    private BookingValidator bookingValidator;

    @InjectMocks
    private AvailabilityServiceImpl availabilityService;

    private UUID employeeId;
    private UUID treatmentId;
    private BookingRequestDto request;

    @BeforeEach
    void setUp() {
        employeeId = UUID.randomUUID();
        treatmentId = UUID.randomUUID();

        lenient().when(bookingProperties.getTimezone()).thenReturn("Europe/Dublin");
        lenient().when(employeeRepository.existsActiveEmployeeTreatment(employeeId, treatmentId)).thenReturn(true);
        lenient().when(slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        request = BookingRequestDto.builder()
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

    @Test
    void validateBookingRequestShouldIgnoreTreatmentDurationMismatchForFixedSlotFlow() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(90);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> availabilityService.validateBookingRequest(request));
    }

    @Test
    void validateBookingRequestShouldRejectInvalidTimeRangeBeforeLookup() {
        request.setStartTime(LocalTime.of(11, 0));
        request.setEndTime(LocalTime.of(10, 0));
        doThrow(new BookingValidationException("End time must be after start time."))
                .when(bookingValidator)
                .validateTimeRange(request.getStartTime(), request.getEndTime());

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(request));

        assertEquals("End time must be after start time.", exception.getMessage());
        verify(bookingValidator).validateTimeRange(request.getStartTime(), request.getEndTime());
        verify(employeeRepository, never()).findById(org.mockito.ArgumentMatchers.any(UUID.class));
        verify(treatmentRepository, never()).findById(org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void validateBookingRequestShouldFailWhenOutsideEmployeeHours() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(11, 0),
                LocalTime.of(20, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));

        assertThrows(BookingValidationException.class, () -> availabilityService.validateBookingRequest(request));
    }

    @Test
    void validateBookingRequestShouldFailWhenSlotConflictsWithConfirmedBooking() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        BookingEntity existing = new BookingEntity();
        existing.setStatus(BookingStatus.CONFIRMED);
        existing.setStartTime(LocalTime.of(10, 15));
        existing.setEndTime(LocalTime.of(10, 45));

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(existing));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(request));

        assertEquals("This slot has already been booked by someone else.", exception.getMessage());
    }

    @Test
    void validateBookingRequestShouldFailWhenSlotIsHeldByAnotherGuest() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        BookingEntity heldBooking = new BookingEntity();
        heldBooking.setStatus(BookingStatus.PENDING);
        heldBooking.setStartTime(LocalTime.of(10, 0));
        heldBooking.setEndTime(LocalTime.of(11, 0));
        heldBooking.setExpiresAt(ACTIVE_HOLD_EXPIRY);

        BookingRequestDto heldRequest = BookingRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(request.getBookingDate())
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .paymentMethodId("pm_card_visa")
                .customer(request.getCustomer())
                .build();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(heldBooking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(heldRequest));

        assertEquals(
                "This slot has just been held by another guest. Sorry for the inconvenience.",
                exception.getMessage());
    }

    @Test
    void validateBookingRequestShouldFailWhenSlotConflictsWithActiveSlotHold() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(UUID.randomUUID());
        slotHold.setActive(true);
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(11, 0));
        slotHold.setExpiresAt(ACTIVE_HOLD_EXPIRY);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of());
        when(slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                org.mockito.ArgumentMatchers.eq(employeeId),
                org.mockito.ArgumentMatchers.eq(request.getBookingDate()),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(slotHold));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(request));

        assertEquals(
                "This slot has just been held by another guest. Sorry for the inconvenience.",
                exception.getMessage());
    }

    @Test
    void validateBookingRequestShouldIgnoreExpiredPendingBooking() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        BookingEntity expiredPending = new BookingEntity();
        expiredPending.setStatus(BookingStatus.PENDING);
        expiredPending.setStartTime(LocalTime.of(10, 0));
        expiredPending.setEndTime(LocalTime.of(11, 0));
        expiredPending.setExpiresAt(EXPIRED_HOLD_EXPIRY);
        expiredPending.setPaymentCapturedAt(null);
        expiredPending.setStripePaymentStatus(null);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(expiredPending));

        assertDoesNotThrow(() -> availabilityService.validateBookingRequest(request));
    }

    @Test
    void validateBookingRequestShouldFailWhenSlotOverlapsBreak() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(60);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        BookingRequestDto breakRequest = BookingRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(request.getBookingDate())
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .paymentMethodId("pm_card_visa")
                .customer(request.getCustomer())
                .build();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));

        assertThrows(BookingValidationException.class, () -> availabilityService.validateBookingRequest(breakRequest));
    }

    @Test
    void validateBookingRequestShouldPassWhenBreakIsAbsent() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(60);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null,
                null);

        BookingRequestDto noBreakRequest = BookingRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(request.getBookingDate())
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .paymentMethodId("pm_card_visa")
                .customer(request.getCustomer())
                .build();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> availabilityService.validateBookingRequest(noBreakRequest));
    }

    @Test
    void getAvailabilityShouldReturnHourlySlotsWithBreakAndBookedStatuses() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0));

        BookingEntity existing = new BookingEntity();
        existing.setStatus(BookingStatus.CONFIRMED);
        existing.setStartTime(LocalTime.of(11, 0));
        existing.setEndTime(LocalTime.of(12, 0));

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(existing));

        var result = availabilityService.getAvailability(employeeId, request.getBookingDate(), treatmentId);

        assertEquals(3, result.size());
        assertEquals("AVAILABLE", result.get(0).getStatus());
        assertEquals("BREAK", result.get(1).getStatus());
        assertEquals("BOOKED", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
        assertFalse(result.get(2).isAvailable());
    }

    @Test
    void getAvailabilityShouldReturnHeldStatusForActivePendingBooking() {
        LocalDate futureDate = SECOND_ACTIVE_BOOKING_DATE;
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                futureDate,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                null,
                null);

        BookingEntity heldBooking = new BookingEntity();
        heldBooking.setStatus(BookingStatus.PENDING);
        heldBooking.setStartTime(LocalTime.of(10, 0));
        heldBooking.setEndTime(LocalTime.of(11, 0));
        heldBooking.setExpiresAt(ACTIVE_HOLD_EXPIRY);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, futureDate))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                futureDate,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(heldBooking));

        var result = availabilityService.getAvailability(employeeId, futureDate, treatmentId);

        assertEquals("AVAILABLE", result.get(0).getStatus());
        assertEquals("HELD", result.get(1).getStatus());
        assertEquals("AVAILABLE", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
    }

    @Test
    void getAvailabilityShouldReturnHeldStatusForActiveAdminSlotHold() {
        LocalDate futureDate = SECOND_ACTIVE_BOOKING_DATE;
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                futureDate,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                null,
                null);

        SlotHoldEntity adminSlotHold = new SlotHoldEntity();
        adminSlotHold.setActive(true);
        adminSlotHold.setHoldScope(SlotHoldScope.ADMIN);
        adminSlotHold.setStartTime(LocalTime.of(10, 0));
        adminSlotHold.setEndTime(LocalTime.of(11, 0));
        adminSlotHold.setExpiresAt(ACTIVE_HOLD_EXPIRY);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, futureDate))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                futureDate,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of());
        when(slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                org.mockito.ArgumentMatchers.eq(employeeId),
                org.mockito.ArgumentMatchers.eq(futureDate),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(adminSlotHold));

        var result = availabilityService.getAvailability(employeeId, futureDate, treatmentId);

        assertEquals("AVAILABLE", result.get(0).getStatus());
        assertEquals("HELD", result.get(1).getStatus());
        assertEquals("AVAILABLE", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
    }

    @Test
    void getAvailabilityShouldRejectNonBookableEmployee() {
        EmployeeEntity employee = buildActiveEmployee();
        employee.setBookable(false);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.getAvailability(employeeId, request.getBookingDate(), treatmentId));

        assertEquals("Employee is not available for booking", exception.getMessage());
    }

    @Test
    void getAvailabilityShouldRejectUnsupportedEmployeeTreatmentPair() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsActiveEmployeeTreatment(employeeId, treatmentId)).thenReturn(false);
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.getAvailability(employeeId, request.getBookingDate(), treatmentId));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
    }

    @Test
    void getAvailabilityShouldTreatPaidPendingBookingAsBooked() {
        LocalDate futureDate = SECOND_ACTIVE_BOOKING_DATE;
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                futureDate,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                null,
                null);

        BookingEntity paidPendingBooking = new BookingEntity();
        paidPendingBooking.setStatus(BookingStatus.PENDING);
        paidPendingBooking.setStartTime(LocalTime.of(10, 0));
        paidPendingBooking.setEndTime(LocalTime.of(11, 0));
        paidPendingBooking.setExpiresAt(null);
        paidPendingBooking.setStripePaymentStatus("succeeded");
        paidPendingBooking.setPaymentCapturedAt(PAYMENT_CAPTURED_AT);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, futureDate))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                futureDate,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(paidPendingBooking));

        var result = availabilityService.getAvailability(employeeId, futureDate, treatmentId);

        assertEquals("AVAILABLE", result.get(0).getStatus());
        assertEquals("BOOKED", result.get(1).getStatus());
        assertEquals("AVAILABLE", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
    }

    @Test
    void getAvailabilityShouldMarkEndedBookedSlotsAsPast() {
        LocalDate pastDate = PAST_BOOKING_DATE;
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                pastDate,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                null,
                null);

        BookingEntity finishedBooking = new BookingEntity();
        finishedBooking.setStatus(BookingStatus.CONFIRMED);
        finishedBooking.setStartTime(LocalTime.of(10, 0));
        finishedBooking.setEndTime(LocalTime.of(11, 0));

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, pastDate))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                pastDate,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(finishedBooking));

        var result = availabilityService.getAvailability(employeeId, pastDate, treatmentId);

        assertEquals("PAST", result.get(0).getStatus());
        assertEquals("PAST", result.get(1).getStatus());
        assertEquals("PAST", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
    }

    @Test
    void validateSlotSelectionExcludingBookingShouldIgnoreCurrentBooking() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(60);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null,
                null);

        UUID currentBookingId = UUID.randomUUID();
        BookingEntity existing = new BookingEntity();
        existing.setId(currentBookingId);
        existing.setStatus(BookingStatus.CONFIRMED);
        existing.setStartTime(request.getStartTime());
        existing.setEndTime(request.getEndTime());

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(existing));

        assertDoesNotThrow(() -> availabilityService.validateSlotSelectionExcludingBooking(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime(),
                currentBookingId));
    }

    @Test
    void validateBookingRequestShouldFailWhenAdminCancelledSlotRemainsLocked() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null,
                null);

        BookingEntity cancelledLockedBooking = new BookingEntity();
        cancelledLockedBooking.setStatus(BookingStatus.CANCELLED);
        cancelledLockedBooking.setSlotLocked(true);
        cancelledLockedBooking.setStartTime(request.getStartTime());
        cancelledLockedBooking.setEndTime(request.getEndTime());

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(cancelledLockedBooking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(request));

        assertEquals("This slot has already been booked by someone else.", exception.getMessage());
    }

    @Test
    void validateBookingRequestShouldFailWhenSlotConflictsWithActiveAdminSlotHold() {
        LocalDate futureDate = SECOND_ACTIVE_BOOKING_DATE;
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                futureDate,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                null,
                null);

        SlotHoldEntity adminSlotHold = new SlotHoldEntity();
        adminSlotHold.setActive(true);
        adminSlotHold.setHoldScope(SlotHoldScope.ADMIN);
        adminSlotHold.setStartTime(LocalTime.of(10, 0));
        adminSlotHold.setEndTime(LocalTime.of(11, 0));
        adminSlotHold.setExpiresAt(ACTIVE_HOLD_EXPIRY);

        BookingRequestDto heldRequest = BookingRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(futureDate)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .paymentMethodId("pm_card_visa")
                .customer(request.getCustomer())
                .build();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, futureDate))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                futureDate,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of());
        when(slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                org.mockito.ArgumentMatchers.eq(employeeId),
                org.mockito.ArgumentMatchers.eq(futureDate),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(adminSlotHold));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(heldRequest));

        assertEquals(
                "This slot has just been held by another guest. Sorry for the inconvenience.",
                exception.getMessage());
    }

    @Test
    void validateBookingRequestShouldFailWhenSlotConflictsWithDoneBooking() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        BookingEntity existing = new BookingEntity();
        existing.setStatus(BookingStatus.DONE);
        existing.setStartTime(LocalTime.of(10, 0));
        existing.setEndTime(LocalTime.of(10, 30));

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(existing));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(request));

        assertEquals("This slot has already been booked by someone else.", exception.getMessage());
    }

    @Test
    void getAvailabilityShouldTreatFutureDoneBookingAsBooked() {
        LocalDate futureDate = SECOND_ACTIVE_BOOKING_DATE;
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);
        EmployeeDailyScheduleEntity hours = buildWorkingDay(
                futureDate,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                null,
                null);

        BookingEntity doneBooking = new BookingEntity();
        doneBooking.setStatus(BookingStatus.DONE);
        doneBooking.setStartTime(LocalTime.of(10, 0));
        doneBooking.setEndTime(LocalTime.of(11, 0));

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, futureDate))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                futureDate,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(doneBooking));

        var result = availabilityService.getAvailability(employeeId, futureDate, treatmentId);

        assertEquals("AVAILABLE", result.get(0).getStatus());
        assertEquals("BOOKED", result.get(1).getStatus());
        assertEquals("AVAILABLE", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
    }

    @Test
    void validateBookingRequestShouldFailWhenEmployeeDoesNotProvideTreatment() {
        EmployeeEntity employee = buildActiveEmployee();
        TreatmentEntity treatment = buildActiveTreatment(30);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsActiveEmployeeTreatment(employeeId, treatmentId)).thenReturn(false);
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(request));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
    }

    private EmployeeEntity buildActiveEmployee() {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setActive(true);
        employee.setBookable(true);
        return employee;
    }

    private TreatmentEntity buildActiveTreatment(int durationMinutes) {
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);
        treatment.setDurationMinutes(durationMinutes);
        return treatment;
    }

    private EmployeeDailyScheduleEntity buildWorkingDay(
            LocalDate date,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime breakStart,
            LocalTime breakEnd) {
        EmployeeDailyScheduleEntity hours = new EmployeeDailyScheduleEntity();
        hours.setWorkingDate(date);
        hours.setWorkingDay(true);
        hours.setOpenTime(openTime);
        hours.setCloseTime(closeTime);
        hours.setBreakStartTime(breakStart);
        hours.setBreakEndTime(breakEnd);
        return hours;
    }
}
