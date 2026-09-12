package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Lightweight customer lookup payload for admin phone booking autofill.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingCustomerLookupResponseDto {

    @ToString.Exclude
    private String customerName;

    @ToString.Exclude
    private String customerPhone;

    @ToString.Exclude
    private String customerEmail;
}
