package com.booking.engine.platform.repository;

import com.booking.engine.entity.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByUserId(UUID userId);
}
