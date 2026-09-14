package com.booking.engine.platform.repository;

import com.booking.engine.entity.Service;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, UUID> {
    List<Service> findAllByOrganizationId(UUID organizationId);
}
