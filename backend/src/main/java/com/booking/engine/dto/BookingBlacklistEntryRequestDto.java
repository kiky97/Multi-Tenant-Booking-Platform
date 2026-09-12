package com.booking.engine.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Request DTO for booking blacklist entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingBlacklistEntryRequestDto {

    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    @ToString.Exclude
    private String email;

    @Size(max = 50, message = "Phone cannot exceed 50 characters")
    @ToString.Exclude
    private String phone;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    @ToString.Exclude
    private String reason;
}
