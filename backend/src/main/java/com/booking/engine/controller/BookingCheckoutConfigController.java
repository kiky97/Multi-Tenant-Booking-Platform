package com.booking.engine.controller;

import com.booking.engine.dto.BookingCheckoutConfigResponseDto;
import com.booking.engine.properties.StripeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read-only booking checkout configuration.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/public/booking-checkout-config", produces = MediaType.APPLICATION_JSON_VALUE)
public class BookingCheckoutConfigController {

    private final StripeProperties stripeProperties;

    @GetMapping
    public BookingCheckoutConfigResponseDto getCheckoutConfig() {
        return BookingCheckoutConfigResponseDto.builder()
                .currency(stripeProperties.getCurrency())
                .stripePublishableKey(stripeProperties.getPublishableKey())
                .build();
    }
}
