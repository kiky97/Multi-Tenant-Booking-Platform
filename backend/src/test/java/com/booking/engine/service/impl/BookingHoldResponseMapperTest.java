package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.TreatmentEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingHoldResponseMapperTest {

    private final BookingHoldResponseMapper mapper = new BookingHoldResponseMapper();

    @Test
    void toHoldResponseDtoShouldMapSlotHoldFieldsExactly() {
        UUID slotHoldId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        LocalDate bookingDate = LocalDate.of(2026, 5, 20);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(10, 45);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 18, 14, 30);
        LocalDateTime capturedAt = LocalDateTime.of(2026, 5, 18, 14, 12);
        LocalDateTime releasedAt = LocalDateTime.of(2026, 5, 18, 14, 13);
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 18, 14, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 5, 18, 14, 5);

        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setName("Jacob");

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setName("Haircut");

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(bookingDate);
        slotHold.setStartTime(startTime);
        slotHold.setEndTime(endTime);
        slotHold.setCustomerName("John Doe");
        slotHold.setCustomerEmail("john@example.com");
        slotHold.setCustomerPhone("+353870000000");
        slotHold.setExpiresAt(expiresAt);
        slotHold.setStripePaymentIntentId("pi_123");
        slotHold.setStripePaymentStatus("succeeded");
        slotHold.setHoldAmount(new BigDecimal("35.00"));
        slotHold.setPaymentCapturedAt(capturedAt);
        slotHold.setPaymentReleasedAt(releasedAt);
        slotHold.setCreatedAt(createdAt);
        slotHold.setUpdatedAt(updatedAt);

        BookingResponseDto result = mapper.toHoldResponseDto(slotHold);

        assertEquals(slotHoldId, result.getId());
        assertEquals(employeeId, result.getEmployeeId());
        assertEquals("Jacob", result.getEmployeeName());
        assertEquals(treatmentId, result.getTreatmentId());
        assertEquals("Haircut", result.getTreatmentName());
        assertEquals(bookingDate, result.getBookingDate());
        assertEquals(startTime, result.getStartTime());
        assertEquals(endTime, result.getEndTime());
        assertEquals("John Doe", result.getCustomerName());
        assertEquals("john@example.com", result.getCustomerEmail());
        assertEquals("+353870000000", result.getCustomerPhone());
        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertEquals(expiresAt, result.getExpiresAt());
        assertEquals("pi_123", result.getStripePaymentIntentId());
        assertEquals("succeeded", result.getStripePaymentStatus());
        assertEquals(new BigDecimal("35.00"), result.getHoldAmount());
        assertEquals(capturedAt, result.getPaymentCapturedAt());
        assertEquals(releasedAt, result.getPaymentReleasedAt());
        assertEquals(createdAt, result.getCreatedAt());
        assertEquals(updatedAt, result.getUpdatedAt());
    }
}
