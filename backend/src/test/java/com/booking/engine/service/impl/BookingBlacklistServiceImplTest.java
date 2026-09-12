package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.BookingBlacklistEntryRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryResponseDto;
import com.booking.engine.entity.BookingBlacklistEntryEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.repository.BookingBlacklistEntryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingBlacklistServiceImplTest {

    @Mock
    private BookingBlacklistEntryRepository blacklistRepository;

    private BookingBlacklistServiceImpl bookingBlacklistService;

    @BeforeEach
    void setUp() {
        bookingBlacklistService = new BookingBlacklistServiceImpl(blacklistRepository);
    }

    @Test
    void validateAllowedCustomerShouldRejectBlacklistedEmail() {
        when(blacklistRepository.existsByActiveTrueAndEmailNormalized("blocked@example.com")).thenReturn(true);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingBlacklistService.validateAllowedCustomer("blocked@example.com", null));

        assertEquals(
                "This email address is in the booking blacklist and cannot be used for appointments.",
                exception.getMessage());
    }

    @Test
    void isBlockedCustomerShouldReturnTrueWhenPhoneIsBlacklisted() {
        when(blacklistRepository.existsByActiveTrueAndPhoneNormalized("353831234567")).thenReturn(true);

        boolean result = bookingBlacklistService.isBlockedCustomer(null, "+353 83 123 4567");

        assertEquals(true, result);
    }

    @Test
    void createEntryShouldPersistNormalizedContactData() {
        UUID entryId = UUID.randomUUID();
        when(blacklistRepository.save(any(BookingBlacklistEntryEntity.class))).thenAnswer(invocation -> {
            BookingBlacklistEntryEntity entity = invocation.getArgument(0);
            entity.setId(entryId);
            return entity;
        });

        BookingBlacklistEntryResponseDto response = bookingBlacklistService.createEntry(
                BookingBlacklistEntryRequestDto.builder()
                        .email("Blocked@Example.com")
                        .phone("+353 83 123 4567")
                        .reason("Repeated no-shows")
                        .build());

        assertEquals(entryId, response.getId());
        assertEquals("Blocked@Example.com", response.getEmail());
        assertEquals("+353 83 123 4567", response.getPhone());
        verify(blacklistRepository).save(any(BookingBlacklistEntryEntity.class));
    }

    @Test
    void getActiveEntriesShouldReturnOrderedDtos() {
        UUID entryId = UUID.randomUUID();
        when(blacklistRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of(
                BookingBlacklistEntryEntity.builder()
                        .id(entryId)
                        .active(true)
                        .email("blocked@example.com")
                        .phone("+353831234567")
                        .reason("Repeated no-shows")
                        .build()));

        List<BookingBlacklistEntryResponseDto> result = bookingBlacklistService.getActiveEntries();

        assertEquals(1, result.size());
        assertEquals(entryId, result.get(0).getId());
        assertEquals("blocked@example.com", result.get(0).getEmail());
    }

    @Test
    void deleteEntryShouldDeactivateActiveEntry() {
        UUID entryId = UUID.randomUUID();
        BookingBlacklistEntryEntity entity = BookingBlacklistEntryEntity.builder()
                .id(entryId)
                .active(true)
                .phone("+353831234567")
                .phoneNormalized("353831234567")
                .build();
        when(blacklistRepository.findByIdAndActiveTrue(entryId)).thenReturn(Optional.of(entity));

        bookingBlacklistService.deleteEntry(entryId);

        assertEquals(Boolean.FALSE, entity.getActive());
        verify(blacklistRepository).save(entity);
    }
}
