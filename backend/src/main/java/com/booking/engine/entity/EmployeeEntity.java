package com.booking.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing an employee.
 */
@Entity
@Table(name = "employee")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeEntity extends BaseEntity implements DisplayOrderedEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Builder.Default
    @Column(name = "bookable", nullable = false)
    private Boolean bookable = false;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "employee_treatment", joinColumns = @JoinColumn(name = "employee_id"), inverseJoinColumns = @JoinColumn(name = "treatment_id"))
    private Set<TreatmentEntity> providedTreatments = new LinkedHashSet<>();
}
