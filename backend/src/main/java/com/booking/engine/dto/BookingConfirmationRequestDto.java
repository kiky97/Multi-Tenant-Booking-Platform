package com.booking.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Request DTO for finalizing a previously held booking after Stripe payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmationRequestDto {

    @NotBlank(message = "Stripe payment intent ID is required")
    @Size(max = 255, message = "Stripe payment intent ID cannot exceed 255 characters")
    @ToString.Exclude
    private String paymentIntentId;
}
