package com.booking.engine.platform.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Simulates sending a booking-confirmation email. There is no real email provider wired up
 * (same situation as the Stripe test keys — nothing to send through), so this logs what would be
 * sent rather than pretending to deliver it. Runs in its own consumer group so it receives every
 * event independently of {@link BookingAnalyticsConsumer}. */
@Component
public class NotificationConsumer {
    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    @KafkaListener(topics = "${app.kafka.booking-confirmed-topic:booking.confirmed}", groupId = "booking-notifications")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("event=booking_confirmation_email_simulated bookingId={} customerId={} startTime={}",
                event.bookingId(), event.customerId(), event.startTime());
    }
}
