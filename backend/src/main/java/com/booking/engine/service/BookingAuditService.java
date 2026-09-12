package com.booking.engine.service;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;

/**
 * Service contract for booking audit operations.
 * Defines booking audit related business operations.
 */
public interface BookingAuditService {

    /**
     * Emits an audit record for admin booking creation.
     *
     * @param booking booking entity
     */
    void auditAdminBookingCreated(BookingEntity booking);

    /**
     * Emits an audit record for admin booking updates.
     *
     * @param booking        booking entity
     * @param previousStatus status before the admin update
     */
    void auditAdminBookingUpdated(BookingEntity booking, BookingStatus previousStatus);

    /**
     * Emits an audit record for admin booking cancellation.
     *
     * @param booking        booking entity
     * @param previousStatus status before cancellation
     */
    void auditAdminBookingCancelled(BookingEntity booking, BookingStatus previousStatus);

    /**
     * Masks customer email for safe operational logging.
     *
     * @param email raw customer email
     * @return masked email or null
     */
    String maskCustomerEmailForLogs(String email);

    /**
     * Hashes admin search text before it is written to logs.
     *
     * @param search raw search text
     * @return stable short hash or null
     */
    String hashSearchForLogs(String search);
}
