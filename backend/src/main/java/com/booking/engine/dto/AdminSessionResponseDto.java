package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for cookie-backed admin authentication state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSessionResponseDto {

    private String username;

    private long expiresInSeconds;

    private String csrfToken;
}
