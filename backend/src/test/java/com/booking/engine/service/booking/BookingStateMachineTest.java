package com.booking.engine.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.impl.BookingStateMachineImpl;
import java.math.BigDecimal;
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
class BookingStateMachineTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotHoldRepository slotHoldRepository;

    private BookingStateMachine bookingStateMachine;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");
        bookingStateMachine = new BookingStateMachineImpl(bookingRepository, slotHoldRepository, bookingProperties);
    }

    @Test
    void applySuccessfulStripePaymentStateShouldConfirmPendingBookingAndClearHoldFlags() {
        BookingEntity booking = new BookingEntity();
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        booking.setPaymentReleasedAt(LocalDateTime.now().minusMinutes(1));
        booking.setSlotLocked(true);

        bookingStateMachine.applySuccessfulStripePaymentState(booking, "succeeded", "webhook");

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getPaymentReleasedAt());
        assertEquals(null, booking.getExpiresAt());
        assertEquals(Boolean.FALSE, booking.getSlotLocked());
    }

    @Test
    void finalizePaidSlotHoldShouldPersistConfirmedBookingAndDeleteHold() {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(UUID.randomUUID());
        employee.setName("Alex");

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(UUID.randomUUID());
        treatment.setName("Fade");

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(UUID.randomUUID());
        slotHold.setActive(true);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setCustomerName("John Doe");
        slotHold.setCustomerEmail("john@example.com");
        slotHold.setCustomerPhone("+353831234567");
        slotHold.setBookingDate(LocalDate.now().plusDays(1));
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(10, 30));
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        slotHold.setHoldAmount(new BigDecimal("35.00"));
        slotHold.setHoldClientIp("127.0.0.1");
        slotHold.setHoldClientDeviceId("device-1");
        slotHold.setHoldAccessTokenHash("hashed-token");
        slotHold.setStripePaymentIntentId("pi_slot_hold");

        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID());
            return booking;
        });

        BookingEntity savedBooking = bookingStateMachine.finalizePaidSlotHold(slotHold, "succeeded", "webhook");

        assertEquals(BookingStatus.CONFIRMED, savedBooking.getStatus());
        assertEquals("pi_slot_hold", savedBooking.getStripePaymentIntentId());
        assertEquals(new BigDecimal("35.00"), savedBooking.getHoldAmount());
        assertEquals("hashed-token", savedBooking.getHoldAccessTokenHash());
        assertEquals("John Doe", savedBooking.getCustomerName());
        assertNotNull(savedBooking.getPaymentCapturedAt());
        verify(bookingRepository).save(any(BookingEntity.class));
        verify(slotHoldRepository).delete(slotHold);
    }
}
