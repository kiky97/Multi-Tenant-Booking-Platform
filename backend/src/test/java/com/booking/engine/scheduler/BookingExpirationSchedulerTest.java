package com.booking.engine.scheduler;

import static org.mockito.Mockito.verify;

import com.booking.engine.service.BookingMaintenanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {

    @Mock
    private BookingMaintenanceService bookingMaintenanceService;

    private BookingExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BookingExpirationScheduler(bookingMaintenanceService);
    }

    @Test
    void updateBookingStatusesShouldDelegateToMaintenanceService() {
        scheduler.updateBookingStatuses();

        verify(bookingMaintenanceService).updateBookingStatuses();
    }
}
