package com.booking.engine.entity;

public enum BookingStatus {
    /** Slot claimed and a Booking row exists, but Stripe has not yet confirmed payment. */
    HELD,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW

}
