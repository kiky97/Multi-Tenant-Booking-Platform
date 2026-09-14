package com.booking.engine.platform.repository;

import com.booking.engine.entity.Review;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
