package com.booking.engine.dto;

import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing availability of a single time slot for an employee.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlotDto {

    private LocalTime startTime;
    private LocalTime endTime;
    private boolean available;
    private String status;
}
