package com.booking.engine.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.security.SecurityAuditEvent;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.service.BookingAuditService;
import com.booking.engine.service.impl.BookingAuditServiceImpl;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingAuditServiceTest {

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    private BookingAuditService bookingAuditService;

    @BeforeEach
    void setUp() {
        bookingAuditService = new BookingAuditServiceImpl(securityAuditLogger);
    }

    @Test
    void auditAdminBookingCreatedShouldLogStructuredBookingEvent() {
        when(securityAuditLogger.event(anyString(), anyString()))
                .thenAnswer(invocation -> SecurityAuditEvent.builder()
                        .eventType(invocation.getArgument(0))
                        .outcome(invocation.getArgument(1)));
        when(securityAuditLogger.maskEmail(any())).thenReturn("masked-email");
        when(securityAuditLogger.hashValue(any())).thenReturn("hashed-value");

        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(UUID.randomUUID());

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(UUID.randomUUID());

        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStartTime(LocalTime.of(11, 0));
        booking.setEndTime(LocalTime.of(11, 30));
        booking.setCustomerEmail("john@example.com");
        booking.setCustomerPhone("+353 83 123 4567");
        booking.setStatus(BookingStatus.CONFIRMED);

        bookingAuditService.auditAdminBookingCreated(booking);

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(securityAuditLogger).log(captor.capture());

        SecurityAuditEvent event = captor.getValue();
        assertEquals("ADMIN_BOOKING_CREATE", event.getEventType());
        assertEquals("BOOKING", event.getResourceType());
        assertEquals(booking.getId().toString(), event.getResourceId());
        assertEquals("CONFIRMED", event.getAdditionalFields().get("newStatus"));
        assertEquals("masked-email", event.getAdditionalFields().get("customerEmailMask"));
        assertEquals("hashed-value", event.getAdditionalFields().get("customerPhoneHash"));
    }

    @Test
    void hashSearchForLogsShouldTrimBeforeDelegating() {
        when(securityAuditLogger.hashValue("repeat client")).thenReturn("trimmed-hash");

        String result = bookingAuditService.hashSearchForLogs("  repeat client  ");

        assertEquals("trimmed-hash", result);
        verify(securityAuditLogger).hashValue("repeat client");
    }
}
