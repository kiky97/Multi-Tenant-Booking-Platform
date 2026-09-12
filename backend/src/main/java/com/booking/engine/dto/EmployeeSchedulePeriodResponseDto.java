package com.booking.engine.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the latest persisted employee schedule period configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSchedulePeriodResponseDto {

    private LocalDate startDate;
    private LocalDate endDate;
    private UUID employeeId;
    private Boolean applyToAllEmployees;
    private List<EmployeeSchedulePeriodDayRequestDto> days;
}
