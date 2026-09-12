package com.booking.engine.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for employee schedule per date.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeScheduleResponseDto {

    private UUID id;
    private LocalDate workingDate;
    private boolean workingDay;
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;
}
