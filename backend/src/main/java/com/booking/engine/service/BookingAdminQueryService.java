package com.booking.engine.service;

import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import java.util.Optional;

/**
 * Service contract for admin booking query operations.
 * Defines admin booking read related business operations.
 */
public interface BookingAdminQueryService {

    /**
     * Retrieves active bookings for admin review, optionally filtered by search.
     *
     * @param search search text
     * @return list response with counts
     */
    AdminBookingListResponseDto getAdminBookings(String search);

    /**
     * Finds the latest customer contact snapshot matching a phone number.
     *
     * @param phone phone value
     * @return latest matching customer details, when present
     */
    Optional<AdminBookingCustomerLookupResponseDto> findLatestCustomerByPhone(String phone);
}
