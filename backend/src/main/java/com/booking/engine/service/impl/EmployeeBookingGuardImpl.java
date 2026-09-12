package com.booking.engine.service.impl;

import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.service.EmployeeBookingGuard;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link EmployeeBookingGuard}.
 * Provides employee booking guard related business operations.
 */
@Service
@RequiredArgsConstructor
public class EmployeeBookingGuardImpl implements EmployeeBookingGuard {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(EmployeeBookingGuardImpl.class);

    // ---------------------- Constants ----------------------

    private static final List<BookingStatus> ACTIVE_BOOKING_STATUSES = List.of(BookingStatus.PENDING,
            BookingStatus.CONFIRMED);

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateNoActiveBookings(UUID employeeId) {
        boolean hasActiveBookings = bookingRepository.existsByEmployeeIdAndStatusIn(
                employeeId,
                ACTIVE_BOOKING_STATUSES);

        if (hasActiveBookings) {
            log.warn("event=employee_delete_blocked reason=active_bookings employeeId={}", employeeId);
            throw new IllegalStateException(
                    "Cannot delete employee while active bookings exist (PENDING or CONFIRMED).");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRemovedTreatmentsHaveNoFutureBookings(
            EmployeeEntity employee,
            Set<TreatmentEntity> requestedTreatments) {
        Set<UUID> requestedIds = requestedTreatments.stream()
                .map(TreatmentEntity::getId)
                .collect(java.util.stream.Collectors.toSet());

        LocalDate today = LocalDate.now(getZoneId());
        LocalTime nowTime = LocalTime.now(getZoneId());

        employee.getProvidedTreatments().stream()
                .filter(treatment -> !requestedIds.contains(treatment.getId()))
                .forEach(treatment -> {
                    boolean hasFutureBookings = bookingRepository
                            .existsFutureBookingsByEmployeeIdAndTreatmentIdAndStatusIn(
                                    employee.getId(),
                                    treatment.getId(),
                                    ACTIVE_BOOKING_STATUSES,
                                    today,
                                    nowTime);

                    if (hasFutureBookings) {
                        log.warn(
                                "event=employee_treatment_removal_blocked reason=future_bookings employeeId={} treatmentId={}",
                                employee.getId(),
                                treatment.getId());
                        throw new IllegalStateException(
                                "Cannot remove service '" + treatment.getName()
                                        + "' while future bookings still exist for this employee.");
                    }
                });
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Resolves the configured booking timezone as a {@link ZoneId}.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }
}
