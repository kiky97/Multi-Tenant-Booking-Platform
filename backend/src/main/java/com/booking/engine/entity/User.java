package com.booking.engine.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
 
import java.time.Instant;
import java.util.UUID;
 
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter 
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
 
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
 
    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    private String email;
 
    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;
 
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
 
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
