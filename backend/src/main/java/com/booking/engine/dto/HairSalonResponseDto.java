package com.booking.engine.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for hair salon data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HairSalonResponseDto {

    private UUID id;
    private String name;
    private String description;
    private String email;
    private String phone;
    private String address;
    private List<HairSalonHoursResponseDto> workingHours;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
