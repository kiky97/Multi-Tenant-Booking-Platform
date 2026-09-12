package com.booking.engine.scheduler;

import com.booking.engine.service.BookingMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task for booking status transitions.
 * Runs every 5 minutes to:
 * 1) reconcile paid pending bookings into confirmed state;
 * 2) expire unpaid pending bookings that were not confirmed in time;
 * 3) complete confirmed bookings whose end time is in the past.
 * 4) anonymize retained paid/confirmed/completed bookings after the retention
 * window;
 * 5) physically delete old unpaid expired booking holds.
 */
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

    private final BookingMaintenanceService bookingMaintenanceService;

    @Scheduled(cron = "0 */5 * * * *")
    public void updateBookingStatuses() {
        bookingMaintenanceService.updateBookingStatuses();
    }
}
