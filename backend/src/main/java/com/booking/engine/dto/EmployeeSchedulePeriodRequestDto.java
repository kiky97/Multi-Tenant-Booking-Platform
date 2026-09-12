package com.booking.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bulk request DTO for applying employee schedule rules to a date period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSchedulePeriodRequestDto {

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private UUID employeeId;

    @NotNull(message = "Apply-to-all-employees flag is required")
    private Boolean applyToAllEmployees;

    @Valid
    @NotEmpty(message = "At least one weekday configuration is required")
    private List<EmployeeSchedulePeriodDayRequestDto> days;
}
