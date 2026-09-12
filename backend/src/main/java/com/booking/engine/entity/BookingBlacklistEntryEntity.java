package com.booking.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Entity for blocked booking contacts.
 */
@Entity
@Table(name = "booking_blacklist_entry")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BookingBlacklistEntryEntity extends BaseEntity {

    @Column(length = 255)
    private String email;

    @Column(name = "email_normalized", length = 255)
    private String emailNormalized;

    @Column(length = 50)
    private String phone;

    @Column(name = "phone_normalized", length = 50)
    private String phoneNormalized;

    @Column(length = 500)
    private String reason;
}
