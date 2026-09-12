package com.booking.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Request payload for preparing Stripe checkout on top of an existing held
 * slot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCheckoutSessionRequestDto {

    @Valid
    @NotNull(message = "Customer details are required")
    @ToString.Exclude
    private BookingRequestDto.CustomerDetailsDto customer;

    @NotBlank(message = "Stripe confirmation token is required")
    @Size(max = 255, message = "Stripe confirmation token cannot exceed 255 characters")
    @ToString.Exclude
    private String confirmationTokenId;
}
