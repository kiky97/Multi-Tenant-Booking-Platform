package com.booking.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Temporary slot reservation that blocks availability without creating a
 * booking.
 */
@Entity
@Table(name = "slot_hold")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SlotHoldEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private EmployeeEntity employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_id", nullable = false)
    private TreatmentEntity treatment;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_scope", nullable = false, length = 20)
    private SlotHoldScope holdScope;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "hold_amount", precision = 10, scale = 2)
    private BigDecimal holdAmount;

    @Column(name = "hold_client_ip", length = 64)
    private String holdClientIp;

    @Column(name = "hold_client_device_id", length = 128)
    private String holdClientDeviceId;

    @Column(name = "hold_access_token_hash", length = 128)
    private String holdAccessTokenHash;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_payment_status", length = 100)
    private String stripePaymentStatus;

    @Column(name = "payment_captured_at")
    private LocalDateTime paymentCapturedAt;

    @Column(name = "payment_released_at")
    private LocalDateTime paymentReleasedAt;
}
