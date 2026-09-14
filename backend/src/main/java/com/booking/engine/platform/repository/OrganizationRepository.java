package com.booking.engine.platform.repository;

import com.booking.engine.entity.Organization;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    List<Organization> findAllByProviderId(UUID providerId);
}
