package com.booking.engine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for treatment data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreatmentResponseDto {

    private UUID id;
    private String name;
    private Integer durationMinutes;
    private BigDecimal price;
    private String photoUrl;
    private String description;
    private Integer displayOrder;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
