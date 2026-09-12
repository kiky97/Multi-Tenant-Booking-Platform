package com.booking.engine.service.impl;

import com.booking.engine.entity.BookingStatus;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.service.BookingMaintenanceService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Implementation of {@link BookingMaintenanceService}.
 * Provides booking maintenance related business operations.
 */
@Service
@RequiredArgsConstructor
public class BookingMaintenanceServiceImpl implements BookingMaintenanceService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(BookingMaintenanceServiceImpl.class);

    // ---------------------- Constants ----------------------

    private static final int BOOKING_RETENTION_WEEKS = 2;

    private static final String ANONYMIZED_CUSTOMER_NAME = "Anonymized customer";

    private static final List<BookingStatus> FINANCIAL_AUDIT_RETAINED_STATUSES = List.of(BookingStatus.CONFIRMED,
            BookingStatus.DONE);

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Services ----------------------

    private final TransactionOperations transactionOperations;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateBookingStatuses() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(bookingProperties.getTimezone()));

        int reconciled = executeInOwnTransaction(() -> bookingRepository.reconcilePaidPendingBookings(
                BookingStatus.PENDING,
                BookingStatus.CONFIRMED,
                now));

        int expired = executeInOwnTransaction(() -> bookingRepository.expireUnpaidPendingBookings(
                BookingStatus.PENDING,
                BookingStatus.EXPIRED,
                now));

        int deletedExpiredUnpaidSlotHolds = executeInOwnTransaction(
                () -> slotHoldRepository.deleteExpiredUnpaidSlotHoldsBefore(now));

        int completed = executeInOwnTransaction(() -> bookingRepository.completeConfirmedBookings(
                now.toLocalDate(),
                now.toLocalTime(),
                BookingStatus.CONFIRMED,
                BookingStatus.DONE));

        /*
         * Financial audit retention: paid, confirmed, and completed bookings are
         * never physically deleted by scheduled maintenance. After the short
         * customer-data retention window, only customer PII and hold access data
         * are anonymized; Stripe identifiers, payment statuses, amounts,
         * capture/release timestamps, booking dates, employee/treatment links,
         * and status remain available for dispute and audit review. Only old
         * unpaid expired booking holds are physically removed.
         */
        LocalDate retentionCutoff = now.toLocalDate().minusWeeks(BOOKING_RETENTION_WEEKS);
        int anonymized = executeInOwnTransaction(() -> bookingRepository.anonymizeRetainedFinancialAuditBookingsBefore(
                retentionCutoff,
                FINANCIAL_AUDIT_RETAINED_STATUSES,
                ANONYMIZED_CUSTOMER_NAME));
        int deletedExpiredBookings = executeInOwnTransaction(
                () -> bookingRepository.deleteExpiredUnpaidBookingsBefore(retentionCutoff, BookingStatus.EXPIRED));

        if (reconciled > 0 || expired > 0 || deletedExpiredUnpaidSlotHolds > 0 || completed > 0
                || anonymized > 0 || deletedExpiredBookings > 0) {
            log.info(
                    "event=booking_maintenance_completed reconciled={} expired={} deletedExpiredUnpaidSlotHolds={} completed={} anonymized={} deletedExpiredBookings={}",
                    reconciled,
                    expired,
                    deletedExpiredUnpaidSlotHolds,
                    completed,
                    anonymized,
                    deletedExpiredBookings);
        }
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Runs each maintenance step in its own transaction so one cleanup result
     * can commit before the next step starts.
     */
    private <T> T executeInOwnTransaction(Supplier<T> operation) {
        return transactionOperations.execute(status -> operation.get());
    }
}
