package com.booking.engine.platform.repository;

import com.booking.engine.entity.Provider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {
    Optional<Provider> findByUserId(UUID userId);
}
