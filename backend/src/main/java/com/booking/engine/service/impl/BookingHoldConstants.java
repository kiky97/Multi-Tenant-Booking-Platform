package com.booking.engine.service.impl;

/**
 * Shared booking hold limits and lifetimes used by booking services.
 */
final class BookingHoldConstants {

    static final int PUBLIC_SLOT_HOLD_MINUTES = 10;
    static final int ADMIN_SLOT_HOLD_MINUTES = 2;
    static final int MAX_ACTIVE_HOLDS_PER_IP = 2;
    static final int MAX_ACTIVE_HOLDS_PER_DEVICE = 2;

    private BookingHoldConstants() {
    }
}
