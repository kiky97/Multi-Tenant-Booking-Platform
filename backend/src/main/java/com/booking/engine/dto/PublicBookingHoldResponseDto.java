package com.booking.engine.dto;

import com.booking.engine.entity.BookingStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Public-safe response returned once when a temporary booking hold is created.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicBookingHoldResponseDto {

    private UUID id;
    private UUID employeeId;
    private UUID treatmentId;
    private String employeeName;
    private String treatmentName;
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BookingStatus status;
    private LocalDateTime expiresAt;

    @ToString.Exclude
    private String holdAccessToken;
}
