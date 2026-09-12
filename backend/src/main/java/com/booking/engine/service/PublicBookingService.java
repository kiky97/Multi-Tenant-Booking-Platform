package com.booking.engine.service;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingCheckoutValidationRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.PublicBookingHoldResponseDto;
import com.booking.engine.dto.PublicBookingSummaryResponseDto;
import java.util.UUID;

/**
 * Service contract for public booking operations.
 * Defines public booking related business operations.
 */
public interface PublicBookingService {

    /**
     * Retrieves a booking or active hold by identifier.
     *
     * @param id              booking or hold identifier
     * @param holdAccessToken public hold access token
     * @return public-safe booking status summary
     */
    PublicBookingSummaryResponseDto getBookingById(UUID id, String holdAccessToken);

    /**
     * Creates a direct public booking after payment is confirmed.
     *
     * @param request booking and payment payload
     * @return confirmed booking details
     */
    BookingResponseDto create(BookingRequestDto request);

    /**
     * Temporarily reserves a public booking slot before checkout.
     *
     * @param clientIp       client IP address
     * @param clientDeviceId client device identifier
     * @return temporary hold details
     */
    PublicBookingHoldResponseDto holdSlot(BookingHoldRequestDto request, String clientIp, String clientDeviceId);

    /**
     * Validates that a held booking can still proceed to checkout.
     *
     * @param id              held booking or slot hold identifier
     * @param request         checkout validation payload
     * @param holdAccessToken public hold access token
     */
    void validateHeldBookingCheckout(
            UUID id,
            BookingCheckoutValidationRequestDto request,
            String holdAccessToken);

    /**
     * Creates or reuses the payment intent for held booking checkout.
     *
     * @param id              held booking or slot hold identifier
     * @param request         checkout preparation payload
     * @param holdAccessToken public hold access token
     * @return payment intent status and client response data
     */
    BookingCheckoutSessionResponseDto prepareHeldBookingCheckout(
            UUID id,
            BookingCheckoutSessionRequestDto request,
            String holdAccessToken);

    /**
     * Confirms a held booking after payment status has been verified.
     *
     * @param id              held booking or slot hold identifier
     * @param request         payment confirmation payload
     * @param holdAccessToken public hold access token
     * @return confirmed booking details
     */
    BookingResponseDto confirmHeldBooking(
            UUID id,
            BookingConfirmationRequestDto request,
            String holdAccessToken);

    /**
     * Cancels an unpaid public hold or public booking.
     *
     * @param id              booking or hold identifier
     * @param holdAccessToken public hold access token
     */
    void cancelBooking(UUID id, String holdAccessToken);
}
