package com.booking.engine.validation;

/**
 * Client-facing booking validation messages shared across booking services.
 */
public final class BookingValidationMessages {

    public static final String PUBLIC_BOOKING_UNAVAILABLE = "Booking through the website is temporarily unavailable. Please contact the barbershop directly.";
    public static final String HOLD_LIMIT_IP = "This connection already has two active appointment holds. Please complete or release an existing hold before selecting another slot.";
    public static final String HOLD_LIMIT_DEVICE = "This device already has two active appointment holds. Please complete or release an existing hold before selecting another slot.";
    public static final String HOLD_LIMIT_SLOT = "This time slot is currently unavailable. Please select a different available time.";
    public static final String INVALID_TIME_RANGE = "End time must be after start time.";
    public static final String ADMIN_HOLD_INVALID = "This admin-held slot is no longer available. Please choose the time again.";
    public static final String EMPLOYEE_NOT_AVAILABLE_FOR_BOOKING = "Employee is not available for booking";
    public static final String APPOINTMENT_ALREADY_CONFIRMED = "This appointment has already been confirmed.";
    public static final String APPOINTMENT_ALREADY_CANCELLED = "This appointment has already been cancelled.";
    public static final String APPOINTMENT_ALREADY_COMPLETED = "This appointment has already been completed.";
    public static final String APPOINTMENT_HOLD_EXPIRED = "This appointment hold has expired. Please choose another time.";
    public static final String APPOINTMENT_HOLD_UNAVAILABLE = "This appointment hold is no longer available.";
    public static final String PAID_BOOKINGS_ADMIN_ONLY = "Paid bookings can only be updated from the admin panel.";
    public static final String PAID_BOOKING_AMOUNT_IMMUTABLE = "Payment amount cannot be changed after payment has been captured.";
    public static final String SLOT_HELD_BY_ANOTHER_GUEST = "This slot has just been held by another guest. Sorry for the inconvenience.";
    public static final String SLOT_ALREADY_BOOKED_BY_SOMEONE_ELSE = "This slot has already been booked by someone else.";
    public static final String STRIPE_PAYMENT_MISMATCH = "This Stripe payment does not match the current appointment hold.";
    public static final String CHECKOUT_CUSTOMER_DETAILS_MISSING = "Customer details are missing. Please restart checkout.";
    public static final String STRIPE_PAYMENT_INCOMPLETE = "Stripe payment is not completed yet. Please finish payment first.";

    private BookingValidationMessages() {
    }
}
