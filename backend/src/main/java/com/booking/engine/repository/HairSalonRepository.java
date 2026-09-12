package com.booking.engine.repository;

import com.booking.engine.entity.HairSalonEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for managing singleton {@link HairSalonEntity} persistence. */
@Repository
public interface HairSalonRepository extends JpaRepository<HairSalonEntity, UUID> {
}
