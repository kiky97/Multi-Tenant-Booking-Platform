package com.booking.engine.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for employee data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponseDto {

    private UUID id;
    private String name;
    private String role;
    private String bio;
    private String photoUrl;
    private Integer displayOrder;
    private boolean bookable;
    private List<UUID> treatmentIds;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
