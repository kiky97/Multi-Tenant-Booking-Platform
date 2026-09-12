package com.booking.engine.dto;

import jakarta.validation.Valid;
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
 * Request DTO for creating a new booking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequestDto {

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

    /**
     * Stripe payment method identifier used for immediate payment.
     * Example in test mode: pm_card_visa.
     */
    @NotBlank(message = "Stripe payment method ID is required")
    @Size(max = 255, message = "Stripe payment method ID cannot exceed 255 characters")
    @ToString.Exclude
    private String paymentMethodId;

    @Valid
    @NotNull(message = "Customer details are required")
    @ToString.Exclude
    private CustomerDetailsDto customer;

    /**
     * Customer details submitted together with a booking request.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerDetailsDto {

        @NotBlank(message = "Customer name is required")
        @Size(max = 255, message = "Customer name cannot exceed 255 characters")
        @ToString.Exclude
        private String name;

        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email must be valid")
        @Size(max = 255, message = "Customer email cannot exceed 255 characters")
        @ToString.Exclude
        private String email;

        @Size(max = 50, message = "Customer phone cannot exceed 50 characters")
        @ToString.Exclude
        private String phone;
    }
}
