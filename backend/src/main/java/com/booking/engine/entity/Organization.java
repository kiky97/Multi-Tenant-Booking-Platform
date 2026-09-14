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
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Organization {
 
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
 
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;
 
    @NotBlank
    @Column(nullable = false)
    private String name;
 
    @Column(nullable = false)
    private String timezone;
 
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Service> services = new ArrayList<>();
 
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Staff> staff = new ArrayList<>();
 
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Availability> availability = new ArrayList<>();
 
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Booking> bookings = new ArrayList<>();
 
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Review> reviews = new ArrayList<>();
}
 