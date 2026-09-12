package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Service DTO for successful admin login.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    private String username;

    @ToString.Exclude
    private String accessToken;

    @ToString.Exclude
    private String csrfToken;

    private long expiresInSeconds;
}
