package com.booking.engine.service;

import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.TreatmentEntity;
import java.util.Set;
import java.util.UUID;

/**
 * Service contract for employee booking guard operations.
 * Defines employee booking guard related business operations.
 */
public interface EmployeeBookingGuard {

    /**
     * Validates no active bookings.
     *
     * @param employeeId employee identifier
     */
    void validateNoActiveBookings(UUID employeeId);

    /**
     * Validates removed treatments have no future bookings.
     *
     * @param employee            employee entity
     * @param requestedTreatments requested treatments value
     */
    void validateRemovedTreatmentsHaveNoFutureBookings(
            EmployeeEntity employee,
            Set<TreatmentEntity> requestedTreatments);
}
