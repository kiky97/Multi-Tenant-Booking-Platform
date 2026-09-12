package com.booking.engine.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin response payload for booking overview and search results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingListResponseDto {

    private List<BookingResponseDto> bookings;
    private long confirmedCount;
    private long filteredCount;
}
