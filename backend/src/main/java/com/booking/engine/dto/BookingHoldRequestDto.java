package com.booking.engine.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for temporarily holding a booking slot before payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingHoldRequestDto {

    @NotNull(message = "Employee ID is required")
    private UUID employeeId;

    @NotNull(message = "Treatment ID is required")
    private UUID treatmentId;

    @NotNull(message = "Booking date is required")
    @FutureOrPresent(message = "Booking date must be today or in the future")
    private LocalDate bookingDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;
}
