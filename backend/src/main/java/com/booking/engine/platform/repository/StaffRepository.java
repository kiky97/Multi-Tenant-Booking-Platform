package com.booking.engine.platform.repository;

import com.booking.engine.entity.Staff;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, UUID> {
    List<Staff> findAllByOrganizationId(UUID organizationId);
}
