package com.booking.engine.dto;

import com.booking.engine.entity.BookingStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public-safe booking summary for unauthenticated booking status polling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicBookingSummaryResponseDto {

    private UUID id;
    private UUID employeeId;
    private UUID treatmentId;
    private String employeeName;
    private String treatmentName;
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BookingStatus status;
}
