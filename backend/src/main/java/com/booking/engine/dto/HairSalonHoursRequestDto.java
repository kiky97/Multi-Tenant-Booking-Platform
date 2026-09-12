package com.booking.engine.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for updating one working day configuration. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HairSalonHoursRequestDto {

    @NotNull(message = "Working-day flag is required")
    private Boolean workingDay;

    private LocalTime openTime;

    private LocalTime closeTime;
}
