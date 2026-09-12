package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.service.payment.BookingStripeMetadataKeys;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds non-PII metadata payloads for Stripe PaymentIntents.
 */
@Component
public class BookingStripeMetadataFactory {

    // ---------------------- Public Methods ----------------------

    /*
     * Builds a non-PII metadata payload for Stripe PaymentIntent.
     */
    Map<String, String> buildStripeMetadata(BookingRequestDto request) {
        return Map.of(
                BookingStripeMetadataKeys.EMPLOYEE_ID, request.getEmployeeId().toString(),
                BookingStripeMetadataKeys.TREATMENT_ID, request.getTreatmentId().toString(),
                BookingStripeMetadataKeys.BOOKING_DATE, request.getBookingDate().toString(),
                BookingStripeMetadataKeys.START_TIME, request.getStartTime().toString(),
                BookingStripeMetadataKeys.END_TIME, request.getEndTime().toString());
    }

    /*
     * Builds a non-PII metadata payload for Stripe PaymentIntent from an
     * existing held booking.
     */
    Map<String, String> buildStripeMetadata(BookingEntity booking) {
        return Map.of(
                BookingStripeMetadataKeys.BOOKING_ID, booking.getId().toString(),
                BookingStripeMetadataKeys.EMPLOYEE_ID, booking.getEmployee().getId().toString(),
                BookingStripeMetadataKeys.TREATMENT_ID, booking.getTreatment().getId().toString(),
                BookingStripeMetadataKeys.BOOKING_DATE, booking.getBookingDate().toString(),
                BookingStripeMetadataKeys.START_TIME, booking.getStartTime().toString(),
                BookingStripeMetadataKeys.END_TIME, booking.getEndTime().toString());
    }

    /*
     * Builds a non-PII metadata payload for Stripe PaymentIntent from a
     * temporary slot hold.
     */
    Map<String, String> buildStripeMetadata(SlotHoldEntity slotHold) {
        return Map.of(
                BookingStripeMetadataKeys.SLOT_HOLD_ID, slotHold.getId().toString(),
                BookingStripeMetadataKeys.EMPLOYEE_ID, slotHold.getEmployee().getId().toString(),
                BookingStripeMetadataKeys.TREATMENT_ID, slotHold.getTreatment().getId().toString(),
                BookingStripeMetadataKeys.BOOKING_DATE, slotHold.getBookingDate().toString(),
                BookingStripeMetadataKeys.START_TIME, slotHold.getStartTime().toString(),
                BookingStripeMetadataKeys.END_TIME, slotHold.getEndTime().toString());
    }
}
