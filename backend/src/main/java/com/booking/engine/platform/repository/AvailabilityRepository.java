package com.booking.engine.platform.repository;

import com.booking.engine.entity.Availability;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {
    List<Availability> findAllByOrganizationId(UUID organizationId);
}
