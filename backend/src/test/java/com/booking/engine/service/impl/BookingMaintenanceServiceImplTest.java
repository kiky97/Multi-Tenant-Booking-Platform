package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.BookingStatus;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class BookingMaintenanceServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotHoldRepository slotHoldRepository;

    @Mock
    private TransactionOperations transactionOperations;

    private BookingMaintenanceServiceImpl service;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");
        when(transactionOperations.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });
        service = new BookingMaintenanceServiceImpl(
                bookingRepository,
                slotHoldRepository,
                bookingProperties,
                transactionOperations);
    }

    @Test
    void updateBookingStatusesShouldRunAllMaintenanceOperations() {
        when(bookingRepository.reconcilePaidPendingBookings(
                eq(BookingStatus.PENDING),
                eq(BookingStatus.CONFIRMED),
                any(LocalDateTime.class)))
                .thenReturn(1);
        when(bookingRepository.expireUnpaidPendingBookings(
                eq(BookingStatus.PENDING),
                eq(BookingStatus.EXPIRED),
                any(LocalDateTime.class)))
                .thenReturn(2);
        when(slotHoldRepository.deleteExpiredUnpaidSlotHoldsBefore(any(LocalDateTime.class)))
                .thenReturn(3);
        when(bookingRepository.completeConfirmedBookings(any(), any(), eq(BookingStatus.CONFIRMED),
                eq(BookingStatus.DONE)))
                .thenReturn(4);
        when(bookingRepository.anonymizeRetainedFinancialAuditBookingsBefore(
                any(LocalDate.class),
                any(),
                any(String.class)))
                .thenReturn(5);
        when(bookingRepository.deleteExpiredUnpaidBookingsBefore(any(LocalDate.class), eq(BookingStatus.EXPIRED)))
                .thenReturn(6);

        service.updateBookingStatuses();

        verify(bookingRepository).reconcilePaidPendingBookings(
                eq(BookingStatus.PENDING),
                eq(BookingStatus.CONFIRMED),
                any(LocalDateTime.class));
        verify(bookingRepository).expireUnpaidPendingBookings(
                eq(BookingStatus.PENDING),
                eq(BookingStatus.EXPIRED),
                any(LocalDateTime.class));
        verify(slotHoldRepository).deleteExpiredUnpaidSlotHoldsBefore(any(LocalDateTime.class));
        verify(bookingRepository).completeConfirmedBookings(any(), any(), eq(BookingStatus.CONFIRMED),
                eq(BookingStatus.DONE));
        verify(bookingRepository).anonymizeRetainedFinancialAuditBookingsBefore(
                any(LocalDate.class),
                eq(List.of(BookingStatus.CONFIRMED, BookingStatus.DONE)),
                eq("Anonymized customer"));
        verify(bookingRepository).deleteExpiredUnpaidBookingsBefore(any(LocalDate.class), eq(BookingStatus.EXPIRED));
    }

    @Test
    void updateBookingStatusesShouldRunEachMaintenanceStepInOwnTransaction() {
        stubNoMaintenanceWork();

        service.updateBookingStatuses();

        verify(transactionOperations, times(6)).execute(any());
    }

    @Test
    void updateBookingStatusesShouldUseBusinessTimezoneForRetentionCutoff() {
        stubNoMaintenanceWork();

        service.updateBookingStatuses();

        LocalDate expectedCutoff = LocalDate.now(ZoneId.of("Europe/Dublin")).minusWeeks(2);
        ArgumentCaptor<LocalDate> anonymizeCutoffCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> deleteCutoffCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(bookingRepository).anonymizeRetainedFinancialAuditBookingsBefore(
                anonymizeCutoffCaptor.capture(),
                any(),
                any(String.class));
        verify(bookingRepository).deleteExpiredUnpaidBookingsBefore(deleteCutoffCaptor.capture(),
                eq(BookingStatus.EXPIRED));
        assertEquals(expectedCutoff, anonymizeCutoffCaptor.getValue());
        assertEquals(expectedCutoff, deleteCutoffCaptor.getValue());
    }

    @Test
    void updateBookingStatusesShouldPassSameTimestampToReconcileExpireAndHoldCleanup() {
        stubNoMaintenanceWork();

        service.updateBookingStatuses();

        ArgumentCaptor<LocalDateTime> reconcileCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> expireCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> holdCleanupCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(bookingRepository).reconcilePaidPendingBookings(any(), any(), reconcileCaptor.capture());
        verify(bookingRepository).expireUnpaidPendingBookings(any(), any(), expireCaptor.capture());
        verify(slotHoldRepository).deleteExpiredUnpaidSlotHoldsBefore(holdCleanupCaptor.capture());

        assertEquals(reconcileCaptor.getValue(), expireCaptor.getValue());
        assertEquals(reconcileCaptor.getValue(), holdCleanupCaptor.getValue());
    }

    @Test
    void updateBookingStatusesShouldAnonymizeRetainedFinancialAuditBookings() {
        stubNoMaintenanceWork();

        service.updateBookingStatuses();

        verify(bookingRepository).anonymizeRetainedFinancialAuditBookingsBefore(
                any(LocalDate.class),
                eq(List.of(BookingStatus.CONFIRMED, BookingStatus.DONE)),
                eq("Anonymized customer"));
    }

    @Test
    void updateBookingStatusesShouldOnlyPhysicallyDeleteExpiredUnpaidSlotHolds() {
        stubNoMaintenanceWork();

        service.updateBookingStatuses();

        verify(slotHoldRepository).deleteExpiredUnpaidSlotHoldsBefore(any(LocalDateTime.class));
    }

    @Test
    void updateBookingStatusesShouldKeepBookingRetentionCleanupUnchanged() {
        stubNoMaintenanceWork();

        service.updateBookingStatuses();

        verify(bookingRepository).deleteExpiredUnpaidBookingsBefore(any(LocalDate.class), eq(BookingStatus.EXPIRED));
    }

    private void stubNoMaintenanceWork() {
        when(bookingRepository.reconcilePaidPendingBookings(any(), any(), any(LocalDateTime.class))).thenReturn(0);
        when(bookingRepository.expireUnpaidPendingBookings(any(), any(), any(LocalDateTime.class))).thenReturn(0);
        when(slotHoldRepository.deleteExpiredUnpaidSlotHoldsBefore(any(LocalDateTime.class))).thenReturn(0);
        when(bookingRepository.completeConfirmedBookings(any(), any(), any(), any())).thenReturn(0);
        when(bookingRepository.anonymizeRetainedFinancialAuditBookingsBefore(
                any(LocalDate.class),
                any(),
                any(String.class)))
                .thenReturn(0);
        when(bookingRepository.deleteExpiredUnpaidBookingsBefore(any(LocalDate.class), eq(BookingStatus.EXPIRED)))
                .thenReturn(0);
    }
}
