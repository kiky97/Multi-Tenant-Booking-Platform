package com.booking.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating an employee.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeRequestDto {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @NotBlank(message = "Role is required")
    @Size(max = 255, message = "Role cannot exceed 255 characters")
    private String role;

    @Size(max = 2000, message = "Bio cannot exceed 2000 characters")
    private String bio;

    @Size(max = 500, message = "Photo URL cannot exceed 500 characters")
    private String photoUrl;

    @PositiveOrZero(message = "Display order must be zero or positive")
    private Integer displayOrder;

    private Boolean bookable;
    private List<UUID> treatmentIds;
}
