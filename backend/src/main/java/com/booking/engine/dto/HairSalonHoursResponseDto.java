package com.booking.engine.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Data;

/** Response DTO representing configured working hours for one day. */
@Data
@Builder
public class HairSalonHoursResponseDto {

    private DayOfWeek dayOfWeek;

    private boolean workingDay;

    private LocalTime openTime;

    private LocalTime closeTime;
}
