package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Stripe checkout payload used by the booking modal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCheckoutSessionResponseDto {

    @ToString.Exclude
    private String paymentIntentId;

    @ToString.Exclude
    private String clientSecret;

    private String paymentStatus;
}
