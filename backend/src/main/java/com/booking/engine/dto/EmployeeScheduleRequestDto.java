package com.booking.engine.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for setting employee schedule for a specific date.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeScheduleRequestDto {

    @NotNull(message = "Working date is required")
    private LocalDate workingDate;

    @NotNull(message = "Working-day flag is required")
    private Boolean workingDay;

    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;
}
