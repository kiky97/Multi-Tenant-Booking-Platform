package com.booking.engine.entity;

/**
 * Enum representing the possible states of a booking.
 */
public enum BookingStatus {
    /**
     * Booking exists as an unpaid hold or, in rare fallback cases, while Stripe
     * success is still reconciling.
     */
    PENDING,

    /**
     * Booking payment is finalized and the appointment is locked into the calendar.
     */
    CONFIRMED,

    /** Booking cancelled before completion. */
    CANCELLED,

    /**
     * Booking expired because the temporary unpaid hold was not completed in time.
     */
    EXPIRED,

    /** Booking was successfully completed and the service time is in the past. */
    DONE
}
