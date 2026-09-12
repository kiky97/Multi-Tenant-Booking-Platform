package com.booking.engine.repository;

import com.booking.engine.entity.BookingBlacklistEntryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for booking blacklist entries. */
@Repository
public interface BookingBlacklistEntryRepository extends JpaRepository<BookingBlacklistEntryEntity, UUID> {

    List<BookingBlacklistEntryEntity> findByActiveTrueOrderByCreatedAtDesc();

    Optional<BookingBlacklistEntryEntity> findByIdAndActiveTrue(UUID id);

    boolean existsByActiveTrueAndEmailNormalized(String emailNormalized);

    boolean existsByActiveTrueAndPhoneNormalized(String phoneNormalized);
}
