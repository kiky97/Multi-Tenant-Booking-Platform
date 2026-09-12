package com.booking.engine.service;

import com.booking.engine.dto.BookingBlacklistEntryRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service contract for booking blacklist operations.
 * Defines booking blacklist related business operations.
 */
public interface BookingBlacklistService {

    /**
     * Validates that provided customer contact data is not blacklisted.
     *
     * @param email customer email, may be null
     * @param phone customer phone, may be null
     */
    void validateAllowedCustomer(String email, String phone);

    /**
     * Checks whether customer contact data is currently blocked.
     *
     * @param email customer email, may be null
     * @param phone customer phone, may be null
     * @return true when email or phone matches an active blacklist entry
     */
    boolean isBlockedCustomer(String email, String phone);

    /**
     * Retrieves all active blacklist entries ordered by creation date descending.
     *
     * @return active blacklist entry DTOs
     */
    @PreAuthorize("hasRole('ADMIN')")
    List<BookingBlacklistEntryResponseDto> getActiveEntries();

    /**
     * Creates a new blacklist entry for customer contact data.
     *
     * @param request blacklist entry payload
     * @return created blacklist entry DTO
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingBlacklistEntryResponseDto createEntry(BookingBlacklistEntryRequestDto request);

    /**
     * Deactivates blacklist entry by identifier.
     *
     * @param id blacklist entry identifier
     */
    @PreAuthorize("hasRole('ADMIN')")
    void deleteEntry(UUID id);
}
