package com.booking.engine.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
 
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
 
@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Staff {
 
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
 
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
 
    @NotBlank
    @Column(nullable = false)
    private String name;
 
    private String email;
 
    @OneToMany(mappedBy = "staff", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Availability> availability = new ArrayList<>();
 
    @OneToMany(mappedBy = "staff", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Booking> bookings = new ArrayList<>();
}
 
