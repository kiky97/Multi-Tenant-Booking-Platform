package com.booking.engine.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Request DTO for creating a free booking from the admin panel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingCreateRequestDto {

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

    private UUID holdBookingId;

    @NotBlank(message = "Customer name is required")
    @Size(max = 255, message = "Customer name cannot exceed 255 characters")
    @ToString.Exclude
    private String customerName;

    @NotBlank(message = "Customer phone is required")
    @Size(max = 50, message = "Customer phone cannot exceed 50 characters")
    @ToString.Exclude
    private String customerPhone;

    @Email(message = "Customer email must be a valid email address")
    @Size(max = 255, message = "Customer email cannot exceed 255 characters")
    @ToString.Exclude
    private String customerEmail;
}
