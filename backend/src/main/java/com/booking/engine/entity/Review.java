package com.booking.engine.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
 
import java.time.Instant;
import java.util.UUID;
 
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Review {
 
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
 
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
 
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
 
    @Min(1)
    @Max(5)
    @Column(nullable = false)
    private Integer rating;
 
    @Column(columnDefinition = "TEXT")
    private String comment;
 
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
 
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
 