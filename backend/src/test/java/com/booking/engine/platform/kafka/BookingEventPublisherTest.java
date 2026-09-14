package com.booking.engine.platform.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.Booking;
import com.booking.engine.entity.Customer;
import com.booking.engine.entity.Organization;
import com.booking.engine.entity.Service;
import com.booking.engine.entity.Staff;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class BookingEventPublisherTest {

    private KafkaTemplate<String, BookingConfirmedEvent> kafka;
    private BookingEventPublisher publisher;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final Instant startTime = Instant.parse("2026-01-01T10:00:00Z");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafka = mock(KafkaTemplate.class);
        publisher = new BookingEventPublisher(kafka, "booking.confirmed");
        when(kafka.send(any(String.class), any(String.class), any(BookingConfirmedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesConfirmedEventWithBookingIdAsKey() {
        Booking booking = booking();

        publisher.publishConfirmed(booking, "eur");

        ArgumentCaptor<BookingConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(kafka).send(eq("booking.confirmed"), eq(bookingId.toString()), eventCaptor.capture());

        BookingConfirmedEvent event = eventCaptor.getValue();
        assertThat(event.bookingId()).isEqualTo(bookingId);
        assertThat(event.organizationId()).isEqualTo(organizationId);
        assertThat(event.customerId()).isEqualTo(customerId);
        assertThat(event.staffId()).isEqualTo(staffId);
        assertThat(event.serviceId()).isEqualTo(serviceId);
        assertThat(event.startTime()).isEqualTo(startTime);
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("45.50"));
        assertThat(event.currency()).isEqualTo("eur");
    }

    private Booking booking() {
        Organization organization = new Organization();
        organization.setId(organizationId);
        Customer customer = new Customer();
        customer.setId(customerId);
        Staff staff = new Staff();
        staff.setId(staffId);
        Service service = new Service();
        service.setId(serviceId);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setOrganization(organization);
        booking.setCustomer(customer);
        booking.setStaff(staff);
        booking.setService(service);
        booking.setStartTime(startTime);
        booking.setAmount(new BigDecimal("45.50"));
        return booking;
    }
}
