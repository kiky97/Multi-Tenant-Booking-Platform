package com.booking.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Request payload used to validate a held booking before opening Stripe
 * checkout on the public website.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCheckoutValidationRequestDto {

    @Valid
    @NotNull(message = "Customer details are required")
    @ToString.Exclude
    private BookingRequestDto.CustomerDetailsDto customer;
}
