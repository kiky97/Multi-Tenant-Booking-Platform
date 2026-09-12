package com.booking.engine.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Weekly schedule row used for bulk employee schedule period updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSchedulePeriodDayRequestDto {

    @NotNull(message = "Day of week is required")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Working-day flag is required")
    private Boolean workingDay;

    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;
}
