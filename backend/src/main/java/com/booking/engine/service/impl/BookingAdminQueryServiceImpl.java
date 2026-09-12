package com.booking.engine.service.impl;

import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.service.BookingAdminQueryService;
import com.booking.engine.service.BookingAuditService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BookingAdminQueryService}.
 * Provides admin booking read related business operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingAdminQueryServiceImpl implements BookingAdminQueryService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(BookingAdminQueryServiceImpl.class);

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    // ---------------------- Mappers ----------------------

    private final BookingMapper mapper;

    // ---------------------- Services ----------------------

    private final BookingAuditService bookingAuditService;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminBookingListResponseDto getAdminBookings(String search) {
        List<BookingEntity> activeBookings = bookingRepository.findAllActiveWithEmployeeAndTreatment();
        long confirmedCount = activeBookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .count();

        List<BookingResponseDto> filteredBookings = activeBookings.stream()
                .sorted(buildAdminBookingComparator())
                .filter(booking -> matchesAdminSearch(booking, search))
                .map(mapper::toDto)
                .toList();

        AdminBookingListResponseDto response = AdminBookingListResponseDto.builder()
                .bookings(filteredBookings)
                .confirmedCount(confirmedCount)
                .filteredCount(filteredBookings.size())
                .build();
        log.debug("event=admin_bookings_loaded searchHash={} totalCount={} filteredCount={} confirmedCount={}",
                hashSearchForLogs(search),
                activeBookings.size(),
                response.getFilteredCount(),
                response.getConfirmedCount());
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AdminBookingCustomerLookupResponseDto> findLatestCustomerByPhone(String phone) {
        String normalizedPhone = normalizePhoneSearch(phone);
        if (normalizedPhone == null) {
            return Optional.empty();
        }

        return bookingRepository.findAllByActiveTrueAndCustomerPhoneIsNotNullOrderByCreatedAtDesc()
                .stream()
                .filter(booking -> normalizedPhone.equals(normalizePhoneSearch(booking.getCustomerPhone())))
                .findFirst()
                .map(booking -> AdminBookingCustomerLookupResponseDto.builder()
                        .customerName(booking.getCustomerName())
                        .customerPhone(booking.getCustomerPhone())
                        .customerEmail(booking.getCustomerEmail())
                        .build());
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Builds the admin booking sort order that keeps upcoming bookings first and
     * pushes historical bookings to the bottom while preserving sensible date and
     * time ordering inside each group.
     */
    private Comparator<BookingEntity> buildAdminBookingComparator() {
        LocalDate today = LocalDate.now(getZoneId());
        LocalTime nowTime = LocalTime.now(getZoneId());

        return (left, right) -> {
            boolean leftPast = isPastBooking(left, today, nowTime);
            boolean rightPast = isPastBooking(right, today, nowTime);

            if (leftPast != rightPast) {
                return leftPast ? 1 : -1;
            }

            int dateComparison = leftPast
                    ? right.getBookingDate().compareTo(left.getBookingDate())
                    : left.getBookingDate().compareTo(right.getBookingDate());

            if (dateComparison != 0) {
                return dateComparison;
            }

            int timeComparison = leftPast
                    ? right.getStartTime().compareTo(left.getStartTime())
                    : left.getStartTime().compareTo(right.getStartTime());

            if (timeComparison != 0) {
                return timeComparison;
            }

            LocalDateTime leftCreatedAt = left.getCreatedAt();
            LocalDateTime rightCreatedAt = right.getCreatedAt();

            if (leftCreatedAt == null && rightCreatedAt == null) {
                return 0;
            }

            if (leftCreatedAt == null) {
                return 1;
            }

            if (rightCreatedAt == null) {
                return -1;
            }

            return leftCreatedAt.compareTo(rightCreatedAt);
        };
    }

    /*
     * Determines whether a booking belongs to the past relative to the current
     * business date and time, using end time for same-day bookings.
     */
    private boolean isPastBooking(BookingEntity booking, LocalDate today, LocalTime nowTime) {
        return booking.getBookingDate().isBefore(today)
                || (booking.getBookingDate().isEqual(today) && booking.getEndTime() != null
                        && booking.getEndTime().isBefore(nowTime));
    }

    /*
     * Applies normalized text and digit-only phone matching so admin search can
     * find bookings by customer name, email, raw phone text, or phone digits.
     */
    private boolean matchesAdminSearch(BookingEntity booking, String search) {
        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch == null) {
            return true;
        }

        String normalizedPhoneSearch = normalizePhoneSearch(search);

        return containsIgnoreCase(booking.getCustomerName(), normalizedSearch)
                || containsIgnoreCase(booking.getCustomerEmail(), normalizedSearch)
                || containsIgnoreCase(booking.getCustomerPhone(), normalizedSearch)
                || (normalizedPhoneSearch != null && normalizePhoneSearch(booking.getCustomerPhone()) != null
                        && normalizePhoneSearch(booking.getCustomerPhone()).contains(normalizedPhoneSearch));
    }

    /*
     * Normalizes free-text admin search input by trimming, lowercasing, and
     * converting blank input to {@code null}.
     */
    private String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim().toLowerCase();
        return trimmed.isBlank() ? null : trimmed;
    }

    /*
     * Extracts digits from a phone-like search value so formatted and unformatted
     * phone numbers can be compared consistently.
     */
    private String normalizePhoneSearch(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }

    /*
     * Performs a null-safe case-insensitive containment check used by the admin
     * search filter.
     */
    private boolean containsIgnoreCase(String value, String search) {
        return value != null && search != null && value.toLowerCase().contains(search);
    }

    /*
     * Resolves configured booking timezone.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }

    /*
     * Hashes raw admin search text before it reaches diagnostic logs.
     */
    private String hashSearchForLogs(String search) {
        return bookingAuditService.hashSearchForLogs(search);
    }
}
