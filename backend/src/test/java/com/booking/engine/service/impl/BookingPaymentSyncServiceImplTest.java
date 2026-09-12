package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.StripePaymentIntentDetails;
import com.booking.engine.service.payment.StripePaymentIntentVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingPaymentSyncServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotHoldRepository slotHoldRepository;

    @Mock
    private BookingBlacklistService bookingBlacklistService;

    private BookingPaymentSyncServiceImpl bookingPaymentSyncService;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");
        StripeProperties stripeProperties = new StripeProperties();
        stripeProperties.setCurrency("eur");

        StripePaymentIntentVerifier stripePaymentIntentVerifier = new StripePaymentIntentVerifier(stripeProperties);

        BookingStateMachine bookingStateMachine = new BookingStateMachineImpl(
                bookingRepository,
                slotHoldRepository,
                bookingProperties);
        BookingValidator bookingValidator = new BookingValidatorImpl(
                bookingRepository,
                slotHoldRepository,
                bookingBlacklistService,
                bookingStateMachine,
                new BookingHoldAccessTokenService(),
                bookingProperties);
        bookingPaymentSyncService = new BookingPaymentSyncServiceImpl(
                bookingRepository,
                slotHoldRepository,
                bookingValidator,
                bookingStateMachine,
                stripePaymentIntentVerifier);

        org.mockito.Mockito.lenient().when(slotHoldRepository.findByStripePaymentIntentIdForUpdate(any(String.class)))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                any(UUID.class),
                any(LocalDate.class),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                any(UUID.class),
                any(LocalDate.class),
                any()))
                .thenReturn(List.of());
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldConfirmPendingBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_confirmed");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_confirmed")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_confirmed",
                        "succeeded",
                        1000L,
                        Map.of("bookingId", bookingId.toString())),
                "payment_intent.succeeded");

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldRejectMismatchedAmount() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_amount_mismatch");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_amount_mismatch"))
                .thenReturn(Optional.of(booking));

        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_amount_mismatch",
                        "succeeded",
                        999L,
                        "eur",
                        Map.of("bookingId", bookingId.toString())),
                "payment_intent.succeeded");

        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals(null, booking.getStripePaymentStatus());
        assertEquals(null, booking.getPaymentCapturedAt());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldRejectSucceededEventWhenStatusIsNotSucceeded() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_status_mismatch");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_status_mismatch"))
                .thenReturn(Optional.of(booking));

        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_status_mismatch",
                        "processing",
                        1000L,
                        "eur",
                        Map.of("bookingId", bookingId.toString())),
                "payment_intent.succeeded");

        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals(null, booking.getStripePaymentStatus());
        assertEquals(null, booking.getPaymentCapturedAt());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldRejectMismatchedCurrency() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_currency_mismatch");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_currency_mismatch"))
                .thenReturn(Optional.empty());
        when(slotHoldRepository.findByStripePaymentIntentIdForUpdate("pi_currency_mismatch"))
                .thenReturn(Optional.of(slotHold));

        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_currency_mismatch",
                        "succeeded",
                        3500L,
                        "usd",
                        Map.of("slotHoldId", slotHoldId.toString())),
                "payment_intent.succeeded");

        assertEquals(null, slotHold.getStripePaymentStatus());
        assertEquals(null, slotHold.getPaymentCapturedAt());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldRejectMismatchedMetadataId() {
        UUID slotHoldId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);
        slotHold.setStripePaymentIntentId("pi_metadata_mismatch");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_metadata_mismatch"))
                .thenReturn(Optional.empty());
        when(slotHoldRepository.findByStripePaymentIntentIdForUpdate("pi_metadata_mismatch"))
                .thenReturn(Optional.of(slotHold));

        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_metadata_mismatch",
                        "succeeded",
                        3500L,
                        "eur",
                        Map.of("slotHoldId", UUID.randomUUID().toString())),
                "payment_intent.succeeded");

        assertEquals(null, slotHold.getStripePaymentStatus());
        assertEquals(null, slotHold.getPaymentCapturedAt());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldRejectMetadataRecoveredBookingPaymentIntentMismatch() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_existing");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_incoming")).thenReturn(Optional.empty());
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(
                "pi_incoming",
                "succeeded",
                "payment_intent.succeeded",
                Map.of("bookingId", bookingId.toString()));

        assertEquals("pi_existing", booking.getStripePaymentIntentId());
        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals(null, booking.getStripePaymentStatus());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
        verify(slotHoldRepository, never()).findByStripePaymentIntentIdForUpdate("pi_incoming");
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldRecoverSlotHoldUsingMetadataWhenPaymentIntentWasNotPersisted() {
        UUID slotHoldId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        SlotHoldEntity slotHold = buildPublicSlotHold(slotHoldId);

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_metadata")).thenReturn(Optional.empty());
        when(slotHoldRepository.findByStripePaymentIntentIdForUpdate("pi_metadata")).thenReturn(Optional.empty());
        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                eq(slotHold.getEmployee().getId()),
                eq(slotHold.getBookingDate()),
                eq(List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
                        BookingStatus.DONE))))
                .thenReturn(List.of());
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        bookingPaymentSyncService.syncStripePaymentIntentFromWebhook(
                paymentIntentDetails(
                        "pi_metadata",
                        "succeeded",
                        3500L,
                        Map.of("slotHoldId", slotHoldId.toString())),
                "payment_intent.succeeded");

        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        verify(slotHoldRepository).delete(slotHold);

        BookingEntity persistedBooking = bookingCaptor.getValue();
        assertEquals(bookingId, persistedBooking.getId());
        assertEquals(BookingStatus.CONFIRMED, persistedBooking.getStatus());
        assertEquals("pi_metadata", persistedBooking.getStripePaymentIntentId());
        assertEquals("succeeded", persistedBooking.getStripePaymentStatus());
        assertEquals(slotHold.getHoldAccessTokenHash(), persistedBooking.getHoldAccessTokenHash());
        assertEquals(slotHold.getBookingDate(), persistedBooking.getBookingDate());
        assertEquals(slotHold.getStartTime(), persistedBooking.getStartTime());
        assertEquals(slotHold.getEndTime(), persistedBooking.getEndTime());
        assertNotNull(persistedBooking.getPaymentCapturedAt());
    }

    private BookingEntity buildPendingBooking(UUID bookingId) {
        EmployeeEntity employee = buildActiveBookableEmployee(UUID.randomUUID());

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(UUID.randomUUID());

        BookingEntity booking = new BookingEntity();
        booking.setId(bookingId);
        booking.setActive(true);
        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStartTime(LocalTime.of(10, 0));
        booking.setEndTime(LocalTime.of(11, 0));
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        booking.setHoldAmount(BigDecimal.valueOf(10));
        booking.setSlotLocked(false);
        return booking;
    }

    private SlotHoldEntity buildPublicSlotHold(UUID slotHoldId) {
        EmployeeEntity employee = buildActiveBookableEmployee(UUID.randomUUID());
        employee.setName("Jacob");

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(UUID.randomUUID());
        treatment.setName("Haircut");
        treatment.setPrice(new BigDecimal("35.00"));

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setActive(true);
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setCustomerName("John Doe");
        slotHold.setCustomerEmail("john@example.com");
        slotHold.setCustomerPhone("+353870000000");
        slotHold.setBookingDate(LocalDate.now().plusDays(1));
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(11, 0));
        slotHold.setHoldAmount(new BigDecimal("35.00"));
        slotHold.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        slotHold.setHoldClientIp("203.0.113.10");
        slotHold.setHoldClientDeviceId("device-123");
        slotHold.setHoldAccessTokenHash("hashed-token");
        return slotHold;
    }

    private EmployeeEntity buildActiveBookableEmployee(UUID employeeId) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setActive(true);
        employee.setBookable(true);
        return employee;
    }

    private StripePaymentIntentDetails paymentIntentDetails(
            String paymentIntentId,
            String status,
            Long amount,
            Map<String, String> metadata) {
        return new StripePaymentIntentDetails(paymentIntentId, status, amount, "eur", metadata);
    }

    private StripePaymentIntentDetails paymentIntentDetails(
            String paymentIntentId,
            String status,
            Long amount,
            String currency,
            Map<String, String> metadata) {
        return new StripePaymentIntentDetails(paymentIntentId, status, amount, currency, metadata);
    }
}
