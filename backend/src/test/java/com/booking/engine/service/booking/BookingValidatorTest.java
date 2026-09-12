package com.booking.engine.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.impl.BookingStateMachineImpl;
import com.booking.engine.service.impl.BookingHoldAccessTokenService;
import com.booking.engine.service.impl.BookingValidatorImpl;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingValidatorTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotHoldRepository slotHoldRepository;

    @Mock
    private BookingBlacklistService bookingBlacklistService;

    private BookingValidator bookingValidator;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");
        BookingStateMachine bookingStateMachine = new BookingStateMachineImpl(
                bookingRepository,
                slotHoldRepository,
                bookingProperties);
        bookingValidator = new BookingValidatorImpl(
                bookingRepository,
                slotHoldRepository,
                bookingBlacklistService,
                bookingStateMachine,
                new BookingHoldAccessTokenService(),
                bookingProperties);
    }

    @Test
    void validatePublicCustomerAllowedShouldReturnGenericUnavailableMessage() {
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353831234567")).thenReturn(true);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingValidator.validatePublicCustomerAllowed("john@example.com", "+353831234567"));

        assertEquals(
                "Booking through the website is temporarily unavailable. Please contact the barbershop directly.",
                exception.getMessage());
    }

    @Test
    void validatePendingBookingAvailabilityShouldExpireStalePendingBooking() {
        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStartTime(LocalTime.of(10, 0));
        booking.setEndTime(LocalTime.of(10, 30));
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        booking.setEmployee(new EmployeeEntity());
        booking.getEmployee().setId(UUID.randomUUID());

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingValidator.validatePendingBookingAvailability(booking));

        assertEquals("This appointment hold has expired. Please choose another time.", exception.getMessage());
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        assertEquals(null, booking.getExpiresAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void validateHoldLimitShouldRejectWhenIpAlreadyOwnsTwoActiveHolds() {
        when(bookingRepository.countActiveUnpaidHoldsByClientIp(
                org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                org.mockito.ArgumentMatchers.eq(BookingStatus.PENDING),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(1L);
        when(slotHoldRepository.countActiveByScopeAndHoldClientIp(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(1L);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingValidator.validateHoldLimit("127.0.0.1", "device-1"));

        assertEquals(
                "This connection already has two active appointment holds. Please complete or release an existing hold before selecting another slot.",
                exception.getMessage());
    }

    @Test
    void validateTimeRangeShouldRejectZeroLengthSlot() {
        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingValidator.validateTimeRange(LocalTime.of(10, 0), LocalTime.of(10, 0)));

        assertEquals("End time must be after start time.", exception.getMessage());
    }

    @Test
    void validateTimeRangeShouldRejectInvertedSlot() {
        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingValidator.validateTimeRange(LocalTime.of(11, 0), LocalTime.of(10, 0)));

        assertEquals("End time must be after start time.", exception.getMessage());
    }

    @Test
    void validateTimeRangeShouldAllowValidSlot() {
        assertDoesNotThrow(() -> bookingValidator.validateTimeRange(LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test
    void validateGlobalSlotHoldLimitShouldRejectWhenSlotAlreadyHasActiveHold() {
        UUID employeeId = UUID.randomUUID();
        LocalDate bookingDate = LocalDate.now().plusDays(1);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);

        when(bookingRepository.countActiveUnpaidHoldsForSlot(
                org.mockito.ArgumentMatchers.eq(employeeId),
                org.mockito.ArgumentMatchers.eq(bookingDate),
                org.mockito.ArgumentMatchers.eq(startTime),
                org.mockito.ArgumentMatchers.eq(endTime),
                org.mockito.ArgumentMatchers.eq(BookingStatus.PENDING),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(1L);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingValidator.validateGlobalSlotHoldLimit(employeeId, bookingDate, startTime, endTime));

        assertEquals("This time slot is currently unavailable. Please select a different available time.",
                exception.getMessage());
    }

    @Test
    void validateGlobalSlotHoldLimitShouldAllowFirstHoldForSlot() {
        UUID employeeId = UUID.randomUUID();
        LocalDate bookingDate = LocalDate.now().plusDays(1);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);

        assertDoesNotThrow(
                () -> bookingValidator.validateGlobalSlotHoldLimit(employeeId, bookingDate, startTime, endTime));
    }
}
