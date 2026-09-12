package com.booking.engine.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Response DTO for active booking blacklist entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingBlacklistEntryResponseDto {

    private UUID id;

    @ToString.Exclude
    private String email;

    @ToString.Exclude
    private String phone;

    @ToString.Exclude
    private String reason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
