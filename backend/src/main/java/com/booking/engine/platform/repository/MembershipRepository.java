package com.booking.engine.platform.repository;

import com.booking.engine.entity.Membership;
import com.booking.engine.entity.MembershipRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);
    Optional<Membership> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<Membership> findAllByOrganizationId(UUID organizationId);
    long countByOrganizationIdAndRole(UUID organizationId, MembershipRole role);
    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);
}
