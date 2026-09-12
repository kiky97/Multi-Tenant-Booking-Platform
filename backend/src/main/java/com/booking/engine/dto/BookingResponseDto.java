package com.booking.engine.dto;

import com.booking.engine.entity.BookingStatus;
import java.math.BigDecimal;
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
 * Response DTO for booking data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDto {

    private UUID id;
    private UUID employeeId;
    private UUID treatmentId;
    private String employeeName;
    private String treatmentName;

    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;

    @ToString.Exclude
    private String customerName;

    @ToString.Exclude
    private String customerEmail;

    @ToString.Exclude
    private String customerPhone;

    private BookingStatus status;
    private LocalDateTime expiresAt;

    @ToString.Exclude
    private String stripePaymentIntentId;

    private String stripePaymentStatus;
    private BigDecimal holdAmount;
    private LocalDateTime paymentCapturedAt;
    private LocalDateTime paymentReleasedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
