package com.booking.engine.platform.kafka;

import com.booking.engine.platform.service.BookingAnalyticsService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Feeds the Redis-backed confirmed-booking counter. Runs in its own consumer group so it
 * receives every event independently of {@link NotificationConsumer} — this is the actual
 * fan-out: two groups on the same topic, not two listeners racing for one group's messages. */
@Component
public class BookingAnalyticsConsumer {
    private final BookingAnalyticsService analytics;

    public BookingAnalyticsConsumer(BookingAnalyticsService analytics) { this.analytics = analytics; }

    @KafkaListener(topics = "${app.kafka.booking-confirmed-topic:booking.confirmed}", groupId = "booking-analytics")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        analytics.recordConfirmedBooking(event.organizationId());
    }
}
