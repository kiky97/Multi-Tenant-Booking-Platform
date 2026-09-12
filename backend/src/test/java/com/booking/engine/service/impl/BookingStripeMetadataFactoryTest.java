package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.TreatmentEntity;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingStripeMetadataFactoryTest {

    private final BookingStripeMetadataFactory factory = new BookingStripeMetadataFactory();

    @Test
    void buildStripeMetadataShouldMapBookingRequestFields() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingRequestDto request = BookingRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.of(2026, 5, 20))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 45))
                .build();

        Map<String, String> result = factory.buildStripeMetadata(request);

        assertEquals(Map.of(
                "employeeId", employeeId.toString(),
                "treatmentId", treatmentId.toString(),
                "bookingDate", "2026-05-20",
                "startTime", "10:00",
                "endTime", "10:45"), result);
    }

    @Test
    void buildStripeMetadataShouldMapBookingFields() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);

        BookingEntity booking = new BookingEntity();
        booking.setId(bookingId);
        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setBookingDate(LocalDate.of(2026, 5, 20));
        booking.setStartTime(LocalTime.of(10, 0));
        booking.setEndTime(LocalTime.of(10, 45));

        Map<String, String> result = factory.buildStripeMetadata(booking);

        assertEquals(Map.of(
                "bookingId", bookingId.toString(),
                "employeeId", employeeId.toString(),
                "treatmentId", treatmentId.toString(),
                "bookingDate", "2026-05-20",
                "startTime", "10:00",
                "endTime", "10:45"), result);
    }

    @Test
    void buildStripeMetadataShouldMapSlotHoldFields() {
        UUID slotHoldId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(LocalDate.of(2026, 5, 20));
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(10, 45));

        Map<String, String> result = factory.buildStripeMetadata(slotHold);

        assertEquals(Map.of(
                "slotHoldId", slotHoldId.toString(),
                "employeeId", employeeId.toString(),
                "treatmentId", treatmentId.toString(),
                "bookingDate", "2026-05-20",
                "startTime", "10:00",
                "endTime", "10:45"), result);
    }
}
