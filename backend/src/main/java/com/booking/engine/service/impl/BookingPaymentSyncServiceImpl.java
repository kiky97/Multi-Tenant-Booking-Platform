package com.booking.engine.service.impl;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.BookingPaymentSyncService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.StripePaymentIntentDetails;
import com.booking.engine.service.payment.BookingPaymentConstants;
import com.booking.engine.service.payment.BookingStripeMetadataKeys;
import com.booking.engine.service.payment.StripePaymentIntentVerifier;
import com.booking.engine.stripe.StripePaymentIntentEventTypes;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BookingPaymentSyncService}.
 * Synchronizes booking payment state from Stripe webhook events.
 */
@Primary
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingPaymentSyncServiceImpl implements BookingPaymentSyncService {
    // ---------------------- Logging ----------------------

    private static final Logger log = LoggerFactory.getLogger(BookingPaymentSyncServiceImpl.class);

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    // ---------------------- Services ----------------------

    private final BookingValidator bookingValidator;

    private final BookingStateMachine bookingStateMachine;

    // ---------------------- Verifier ----------------------

    private final StripePaymentIntentVerifier stripePaymentIntentVerifier;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void syncStripePaymentIntentFromWebhook(String paymentIntentId, String paymentStatus, String eventType) {
        syncStripePaymentIntentFromWebhook(
                new StripePaymentIntentDetails(paymentIntentId, paymentStatus, null, null, Map.of()),
                eventType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void syncStripePaymentIntentFromWebhook(
            String paymentIntentId,
            String paymentStatus,
            String eventType,
            Map<String, String> metadata) {
        syncStripePaymentIntentFromWebhook(
                new StripePaymentIntentDetails(paymentIntentId, paymentStatus, null, null, metadata),
                eventType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void syncStripePaymentIntentFromWebhook(StripePaymentIntentDetails paymentIntent, String eventType) {
        String paymentIntentId = paymentIntent.paymentIntentId();
        String paymentStatus = paymentIntent.status();
        Map<String, String> metadata = paymentIntent.metadata();

        BookingEntity booking = bookingRepository.findByStripePaymentIntentIdForUpdate(paymentIntentId).orElse(null);
        if (booking == null) {
            booking = findBookingByWebhookMetadata(metadata, paymentIntentId);
        }
        if (booking != null) {
            if (!paymentIntentIdMatches(booking, paymentIntentId)) {
                return;
            }

            if (StripePaymentIntentEventTypes.SUCCEEDED.equals(eventType)) {
                if (stripePaymentIntentVerifier.isAlreadySuccessfulBooking(booking)) {
                    if (!validateSuccessfulPaymentIntentForAlreadySuccessfulBooking(
                            booking,
                            paymentIntent,
                            BookingPaymentConstants.WEBHOOK_SOURCE)) {
                        return;
                    }
                } else if (!validateSuccessfulPaymentIntentForBooking(
                        booking,
                        paymentIntent,
                        BookingPaymentConstants.WEBHOOK_SOURCE)) {
                    return;
                }
                attachPaymentIntentIfMissing(booking, paymentIntentId);
                applySuccessfulStripePaymentState(booking, paymentStatus, BookingPaymentConstants.WEBHOOK_SOURCE);
            } else if (StripePaymentIntentEventTypes.CANCELED.equals(eventType)
                    || StripePaymentIntentEventTypes.PAYMENT_FAILED.equals(eventType)) {
                attachPaymentIntentIfMissing(booking, paymentIntentId);
                applyFailedStripePaymentState(booking, paymentStatus);
            }

            bookingRepository.save(booking);
            log.info(
                    "event=payment_webhook_synced entityType=booking bookingId={} eventType={} paymentStatus={} status={}",
                    booking.getId(), eventType, paymentStatus, booking.getStatus());
            return;
        }

        SlotHoldEntity slotHold = slotHoldRepository.findByStripePaymentIntentIdForUpdate(paymentIntentId).orElse(null);
        if (slotHold == null) {
            slotHold = findSlotHoldByWebhookMetadata(metadata, paymentIntentId);
        }
        if (slotHold != null) {
            if (!paymentIntentIdMatches(slotHold, paymentIntentId)) {
                return;
            }

            if (StripePaymentIntentEventTypes.SUCCEEDED.equals(eventType)) {
                if (!validateSuccessfulPaymentIntentForSlotHold(slotHold, paymentIntent,
                        BookingPaymentConstants.WEBHOOK_SOURCE)) {
                    return;
                }
                attachPaymentIntentIfMissing(slotHold, paymentIntentId);
                finalizePaidSlotHold(slotHold, paymentStatus, BookingPaymentConstants.WEBHOOK_SOURCE);
            } else if (StripePaymentIntentEventTypes.CANCELED.equals(eventType)
                    || StripePaymentIntentEventTypes.PAYMENT_FAILED.equals(eventType)) {
                attachPaymentIntentIfMissing(slotHold, paymentIntentId);
                applyFailedStripePaymentState(slotHold, paymentStatus);
                slotHoldRepository.save(slotHold);
            }

            log.info("event=payment_webhook_synced entityType=slot_hold slotHoldId={} eventType={} paymentStatus={}",
                    slotHold.getId(), eventType, paymentStatus);
            return;
        }

        log.warn("event=payment_webhook_sync_failed reason=payment_intent_not_found paymentIntentHash={}",
                hashPaymentIntentForLogs(paymentIntentId));
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Recovers booking context when Stripe sends a webhook before the local
     * PaymentIntent id lookup can find a row.
     */
    private BookingEntity findBookingByWebhookMetadata(Map<String, String> metadata, String paymentIntentId) {
        UUID bookingId = parseWebhookMetadataUuid(
                metadata,
                BookingStripeMetadataKeys.BOOKING_ID,
                paymentIntentId);
        if (bookingId == null) {
            return null;
        }

        BookingEntity booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking != null) {
            log.debug("event=payment_webhook_context_recovered entityType=booking bookingId={} paymentIntentHash={}",
                    bookingId,
                    hashPaymentIntentForLogs(paymentIntentId));
        }
        return booking;
    }

    /*
     * Recovers temporary hold context from Stripe metadata during webhook
     * synchronization.
     */
    private SlotHoldEntity findSlotHoldByWebhookMetadata(Map<String, String> metadata, String paymentIntentId) {
        UUID slotHoldId = parseWebhookMetadataUuid(
                metadata,
                BookingStripeMetadataKeys.SLOT_HOLD_ID,
                paymentIntentId);
        if (slotHoldId == null) {
            return null;
        }

        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(slotHoldId).orElse(null);
        if (slotHold != null) {
            log.debug("event=payment_webhook_context_recovered entityType=slot_hold slotHoldId={} paymentIntentHash={}",
                    slotHoldId,
                    hashPaymentIntentForLogs(paymentIntentId));
        }
        return slotHold;
    }

    /*
     * Accepts only valid UUID metadata values before using webhook-supplied
     * identifiers for context recovery.
     */
    private UUID parseWebhookMetadataUuid(Map<String, String> metadata, String key, String paymentIntentId) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }

        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            log.warn("event=payment_webhook_metadata_rejected reason=invalid_uuid key={} paymentIntentHash={}",
                    key,
                    hashPaymentIntentForLogs(paymentIntentId));
            return null;
        }
    }

    /*
     * Verifies a recovered booking is not already associated with a different
     * Stripe PaymentIntent.
     */
    private boolean paymentIntentIdMatches(BookingEntity booking, String paymentIntentId) {
        if (booking.getStripePaymentIntentId() == null || booking.getStripePaymentIntentId().isBlank()) {
            return true;
        }

        if (!paymentIntentId.equals(booking.getStripePaymentIntentId())) {
            log.warn(
                    "event=payment_webhook_rejected reason=payment_intent_mismatch entityType=booking paymentIntentHash={} bookingId={} existingPaymentIntentHash={}",
                    hashPaymentIntentForLogs(paymentIntentId),
                    booking.getId(),
                    hashPaymentIntentForLogs(booking.getStripePaymentIntentId()));
            return false;
        }

        return true;
    }

    /*
     * Verifies a recovered slot hold is not already associated with a different
     * Stripe PaymentIntent.
     */
    private boolean paymentIntentIdMatches(SlotHoldEntity slotHold, String paymentIntentId) {
        if (slotHold.getStripePaymentIntentId() == null || slotHold.getStripePaymentIntentId().isBlank()) {
            return true;
        }

        if (!paymentIntentId.equals(slotHold.getStripePaymentIntentId())) {
            log.warn(
                    "event=payment_webhook_rejected reason=payment_intent_mismatch entityType=slot_hold paymentIntentHash={} slotHoldId={} existingPaymentIntentHash={}",
                    hashPaymentIntentForLogs(paymentIntentId),
                    slotHold.getId(),
                    hashPaymentIntentForLogs(slotHold.getStripePaymentIntentId()));
            return false;
        }

        return true;
    }

    /*
     * Associates a recovered booking with the webhook PaymentIntent.
     */
    private void attachPaymentIntentIfMissing(BookingEntity booking, String paymentIntentId) {
        if (booking.getStripePaymentIntentId() == null || booking.getStripePaymentIntentId().isBlank()) {
            booking.setStripePaymentIntentId(paymentIntentId);
        }
    }

    /*
     * Associates a recovered slot hold with the webhook PaymentIntent.
     */
    private void attachPaymentIntentIfMissing(SlotHoldEntity slotHold, String paymentIntentId) {
        if (slotHold.getStripePaymentIntentId() == null || slotHold.getStripePaymentIntentId().isBlank()) {
            slotHold.setStripePaymentIntentId(paymentIntentId);
        }
    }

    /*
     * Applies the canonical state transition for a Stripe PaymentIntent that has
     * already succeeded, keeping webhook and direct confirmation flows
     * idempotent and aligned.
     */
    private void applySuccessfulStripePaymentState(BookingEntity booking, String paymentStatus, String source) {
        bookingStateMachine.applySuccessfulStripePaymentState(booking, paymentStatus, source);
    }

    /*
     * Delegates failed-payment state changes for persisted bookings to the
     * state machine.
     */
    private void applyFailedStripePaymentState(BookingEntity booking, String paymentStatus) {
        bookingStateMachine.applyFailedStripePaymentState(booking, paymentStatus);
    }

    /*
     * Delegates failed-payment state changes for temporary slot holds to the
     * state machine.
     */
    private void applyFailedStripePaymentState(SlotHoldEntity slotHold, String paymentStatus) {
        bookingStateMachine.applyFailedStripePaymentState(slotHold, paymentStatus);
    }

    /*
     * Finalizes a paid slot hold into a confirmed booking and removes the
     * temporary hold row.
     */
    private BookingEntity finalizePaidSlotHold(SlotHoldEntity slotHold, String paymentStatus, String source) {
        bookingValidator.validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        return bookingStateMachine.finalizePaidSlotHold(slotHold, paymentStatus, source);
    }

    /*
     * Checks all Stripe fields needed before a booking can transition to
     * confirmed from a successful webhook.
     */
    private boolean validateSuccessfulPaymentIntentForBooking(
            BookingEntity booking,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        return stripePaymentIntentVerifier.isSuccessfulForBooking(booking, paymentIntent, source);
    }

    /*
     * Duplicate success webhooks for already-paid bookings may have slot-hold
     * metadata from the original hold, so only stable payment facts are checked.
     */
    private boolean validateSuccessfulPaymentIntentForAlreadySuccessfulBooking(
            BookingEntity booking,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        return stripePaymentIntentVerifier.isSuccessfulForAlreadyPaidBooking(booking, paymentIntent, source);
    }

    /*
     * Checks all Stripe fields needed before a slot hold can become a confirmed
     * booking from a successful webhook.
     */
    private boolean validateSuccessfulPaymentIntentForSlotHold(
            SlotHoldEntity slotHold,
            StripePaymentIntentDetails paymentIntent,
            String source) {
        return stripePaymentIntentVerifier.isSuccessfulForSlotHold(slotHold, paymentIntent, source);
    }

    /*
     * Hashes a Stripe PaymentIntent identifier for operational logs.
     */
    private String hashPaymentIntentForLogs(String paymentIntentId) {
        return SensitiveLogSanitizer.hashValue(paymentIntentId);
    }
}
