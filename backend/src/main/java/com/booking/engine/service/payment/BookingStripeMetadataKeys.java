package com.booking.engine.service.payment;

/**
 * Stripe metadata keys used to correlate PaymentIntents
 * with local booking entities.
 */
public final class BookingStripeMetadataKeys {

    public static final String BOOKING_ID = "bookingId";
    public static final String SLOT_HOLD_ID = "slotHoldId";
    public static final String EMPLOYEE_ID = "employeeId";
    public static final String TREATMENT_ID = "treatmentId";
    public static final String BOOKING_DATE = "bookingDate";
    public static final String START_TIME = "startTime";
    public static final String END_TIME = "endTime";

    private BookingStripeMetadataKeys() {
    }
}