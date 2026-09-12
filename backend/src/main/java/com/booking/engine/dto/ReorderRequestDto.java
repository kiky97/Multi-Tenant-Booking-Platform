package com.booking.engine.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for swapping display order between two entities. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderRequestDto {

    @NotNull(message = "First entity ID is required")
    private UUID id1;

    @NotNull(message = "Second entity ID is required")
    private UUID id2;
}
