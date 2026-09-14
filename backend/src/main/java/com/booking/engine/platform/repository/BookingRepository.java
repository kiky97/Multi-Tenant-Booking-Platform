package com.booking.engine.platform.repository;

import com.booking.engine.entity.Booking;
import com.booking.engine.entity.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findAllByOrganizationIdOrderByStartTime(UUID organizationId);
    boolean existsByStaffIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID staffId, List<BookingStatus> statuses, Instant endTime, Instant startTime);
    Optional<Booking> findByStripePaymentIntentId(String stripePaymentIntentId);
}
