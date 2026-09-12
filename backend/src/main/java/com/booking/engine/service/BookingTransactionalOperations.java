package com.booking.engine.service;

import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.SlotHoldEntity;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Service contract for booking operations that require their own transaction
 * boundaries.
 */
public interface BookingTransactionalOperations {

    record CheckoutPreparationTarget(
            CheckoutTargetType targetType,
            UUID targetId,
            String existingPaymentIntentId,
            BigDecimal holdAmount,
            String customerEmail,
            Map<String, String> stripeMetadata) {
    }

    enum CheckoutTargetType {
        SLOT_HOLD,
        LEGACY_BOOKING
    }

    /**
     * Reserves a temporary slot hold for a direct public booking flow.
     *
     * @param request booking request payload
     * @return reserved slot hold
     */
    SlotHoldEntity reserveDirectBookingSlot(BookingRequestDto request);

    /**
     * Stores Stripe payment details on a reserved slot hold.
     *
     * @param slotHoldId      slot-hold identifier
     * @param paymentIntentId Stripe PaymentIntent identifier
     * @param paymentStatus   Stripe PaymentIntent status
     * @return updated slot hold
     */
    SlotHoldEntity attachStripePaymentToSlotHold(UUID slotHoldId, String paymentIntentId, String paymentStatus);

    /**
     * Finalizes a direct public booking from a paid slot hold.
     *
     * @param slotHoldId    slot-hold identifier
     * @param paymentStatus Stripe PaymentIntent status
     * @param source        short source label for diagnostics
     * @return confirmed booking
     */
    BookingEntity finalizeDirectBookingFromSlotHold(UUID slotHoldId, String paymentStatus, String source);

    /**
     * Releases slot hold.
     *
     * @param slotHoldId slot-hold identifier
     */
    void releaseSlotHold(UUID slotHoldId);

    /**
     * Resolves and validates the entity targeted by held-booking checkout.
     *
     * @param id              booking or slot hold identifier
     * @param request         checkout preparation payload
     * @param holdAccessToken public hold access token
     * @return checkout target details
     */
    CheckoutPreparationTarget prepareCheckoutTarget(
            UUID id,
            BookingCheckoutSessionRequestDto request,
            String holdAccessToken);

    /**
     * Persists Stripe checkout result data on the checkout target.
     *
     * @param target                  checkout target details
     * @param paymentIntentId         Stripe PaymentIntent identifier
     * @param paymentStatus           Stripe PaymentIntent status
     * @param persistNonSuccessStatus whether non-success statuses should be stored
     */
    void persistCheckoutResult(
            CheckoutPreparationTarget target,
            String paymentIntentId,
            String paymentStatus,
            boolean persistNonSuccessStatus);

    /**
     * Persists Stripe checkout result data after verifying all PaymentIntent
     * fields needed for successful finalization.
     *
     * @param target                  checkout target details
     * @param paymentIntent           Stripe PaymentIntent snapshot
     * @param persistNonSuccessStatus whether non-success statuses should be stored
     */
    void persistCheckoutResult(
            CheckoutPreparationTarget target,
            StripePaymentIntentDetails paymentIntent,
            boolean persistNonSuccessStatus);

    /**
     * Verifies local ownership and the persisted Stripe PaymentIntent before the
     * public confirm flow calls Stripe.
     *
     * @param id              booking or slot hold identifier
     * @param request         payment confirmation payload
     * @param holdAccessToken public hold access token
     */
    void validateHeldBookingConfirmationRequest(
            UUID id,
            BookingConfirmationRequestDto request,
            String holdAccessToken);

    /**
     * Confirms a held booking after Stripe payment status has been checked.
     *
     * @param id              booking or slot hold identifier
     * @param request         payment confirmation payload
     * @param paymentStatus   Stripe PaymentIntent status
     * @param holdAccessToken public hold access token
     * @return confirmed booking
     */
    BookingEntity confirmHeldBookingAfterPaymentStatus(
            UUID id,
            BookingConfirmationRequestDto request,
            String paymentStatus,
            String holdAccessToken);

    /**
     * Confirms a held booking after Stripe payment fields have been retrieved.
     *
     * @param id              booking or slot hold identifier
     * @param request         payment confirmation payload
     * @param paymentIntent   Stripe PaymentIntent snapshot
     * @param holdAccessToken public hold access token
     * @return confirmed booking
     */
    BookingEntity confirmHeldBookingAfterPaymentStatus(
            UUID id,
            BookingConfirmationRequestDto request,
            StripePaymentIntentDetails paymentIntent,
            String holdAccessToken);
}
