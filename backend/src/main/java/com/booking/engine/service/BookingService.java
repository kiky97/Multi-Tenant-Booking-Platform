package com.booking.engine.service;

/**
 * Service contract for booking operations.
 * Defines booking related business operations.
 */
public interface BookingService extends PublicBookingService, AdminBookingService, BookingPaymentSyncService {
}
