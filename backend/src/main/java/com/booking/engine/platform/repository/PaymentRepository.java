package com.booking.engine.platform.repository;

import com.booking.engine.entity.Payment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findAllByBookingIdOrderByCreatedAtDesc(UUID bookingId);
}
