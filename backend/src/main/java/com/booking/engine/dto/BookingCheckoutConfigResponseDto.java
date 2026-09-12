package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public checkout configuration used by the booking page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCheckoutConfigResponseDto {

    private String currency;
    private String stripePublishableKey;
}
