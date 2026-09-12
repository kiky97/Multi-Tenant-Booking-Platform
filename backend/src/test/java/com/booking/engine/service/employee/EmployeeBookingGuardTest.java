package com.booking.engine.service.employee;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.service.EmployeeBookingGuard;
import com.booking.engine.service.impl.EmployeeBookingGuardImpl;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeBookingGuardTest {

    @Mock
    private BookingRepository bookingRepository;

    private EmployeeBookingGuard guard;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");
        guard = new EmployeeBookingGuardImpl(bookingRepository, bookingProperties);
    }

    @Test
    void validateNoActiveBookingsThrowsWhenPendingOrConfirmedBookingsExist() {
        UUID employeeId = UUID.randomUUID();
        when(bookingRepository.existsByEmployeeIdAndStatusIn(eq(employeeId), anyList())).thenReturn(true);

        assertThatThrownBy(() -> guard.validateNoActiveBookings(employeeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active bookings");
    }

    @Test
    void validateRemovedTreatmentsHaveNoFutureBookingsThrowsWhenRemovedTreatmentHasFutureBookings() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        TreatmentEntity existingTreatment = new TreatmentEntity();
        existingTreatment.setId(treatmentId);
        existingTreatment.setName("Hair Color");

        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setProvidedTreatments(new LinkedHashSet<>(Set.of(existingTreatment)));

        when(bookingRepository.existsFutureBookingsByEmployeeIdAndTreatmentIdAndStatusIn(
                eq(employeeId),
                eq(treatmentId),
                anyList(),
                any(LocalDate.class),
                any(LocalTime.class))).thenReturn(true);

        assertThatThrownBy(() -> guard.validateRemovedTreatmentsHaveNoFutureBookings(employee, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hair Color");
    }

    @Test
    void validateRemovedTreatmentsHaveNoFutureBookingsSkipsTreatmentsThatRemainAssigned() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setName("Hair Color");

        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setProvidedTreatments(new LinkedHashSet<>(Set.of(treatment)));

        guard.validateRemovedTreatmentsHaveNoFutureBookings(employee, Set.of(treatment));

        verifyNoMoreInteractions(bookingRepository);
    }
}
