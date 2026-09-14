package com.booking.engine.platform.kafka;

import com.booking.engine.entity.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes booking lifecycle events. Sending is fire-and-forget: a Kafka outage must never fail
 * or slow down the Stripe webhook request that triggers it. */
@Component
public class BookingEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final KafkaTemplate<String, BookingConfirmedEvent> kafka;
    private final String topic;

    public BookingEventPublisher(KafkaTemplate<String, BookingConfirmedEvent> kafka,
            @Value("${app.kafka.booking-confirmed-topic:booking.confirmed}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void publishConfirmed(Booking booking, String currency) {
        BookingConfirmedEvent event = new BookingConfirmedEvent(
                booking.getId(),
                booking.getOrganization().getId(),
                booking.getCustomer().getId(),
                booking.getStaff().getId(),
                booking.getService().getId(),
                booking.getStartTime(),
                booking.getAmount(),
                currency);
        kafka.send(topic, booking.getId().toString(), event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.warn("event=booking_event_publish_failed bookingId={} topic={} reason={}",
                                booking.getId(), topic, exception.getClass().getSimpleName());
                    }
                });
    }
}
