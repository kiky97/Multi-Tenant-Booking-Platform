package com.booking.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.entity.HairSalonHoursEntity;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.HairSalonRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeScheduleAutofillSchedulerTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeDailyScheduleRepository employeeDailyScheduleRepository;

    @Mock
    private HairSalonRepository hairSalonRepository;

    private HairSalonProperties hairSalonProperties;
    private BookingProperties bookingProperties;
    private EmployeeScheduleAutofillScheduler scheduler;

    @BeforeEach
    void setUp() {
        hairSalonProperties = new HairSalonProperties();
        hairSalonProperties.setId(UUID.randomUUID());
        bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");
        scheduler = new EmployeeScheduleAutofillScheduler(
                employeeRepository,
                employeeDailyScheduleRepository,
                hairSalonRepository,
                bookingProperties,
                hairSalonProperties);
    }

    @Test
    void ensureNext7DaysSchedulesCreatesMissingDatesForNextWeekUsingSalonHours() {
        EmployeeEntity employee = employee(true);
        HairSalonEntity salon = salonWithWeekdayHours(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
        LocalDate today = today();

        when(employeeDailyScheduleRepository.deleteByWorkingDateBefore(today)).thenReturn(0L);
        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(employee));
        when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.of(salon));
        when(employeeDailyScheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                List.of(employee.getId()), today, today.plusDays(6)))
                .thenReturn(List.of());
        List<EmployeeDailyScheduleEntity> saved = captureSavedSchedules();

        scheduler.ensureNext7DaysSchedules();

        assertEquals(7, saved.size());
        verify(employeeDailyScheduleRepository).deleteByWorkingDateBefore(today);
        assertNotNull(saved.get(0).getWorkingDate());
        assertEquals(LocalTime.of(9, 0), saved.get(0).getOpenTime());
        assertEquals(LocalTime.of(17, 0), saved.get(0).getCloseTime());
    }

    @Test
    void ensureNext7DaysSchedulesSkipsNonBookableEmployees() {
        EmployeeEntity nonBookable = employee(false);
        HairSalonEntity salon = salonWithWeekdayHours(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
        LocalDate today = today();

        when(employeeDailyScheduleRepository.deleteByWorkingDateBefore(today)).thenReturn(0L);
        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(nonBookable));
        when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.of(salon));
        when(employeeDailyScheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                List.of(nonBookable.getId()), today, today.plusDays(6)))
                .thenReturn(List.of());

        scheduler.ensureNext7DaysSchedules();

        verify(employeeDailyScheduleRepository, never()).saveAll(any());
    }

    @Test
    void ensureNext7DaysSchedulesDoesNotOverwriteExistingDatesAndAvoidsDuplicates() {
        EmployeeEntity employee = employee(true);
        HairSalonEntity salon = salonWithWeekdayHours(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
        LocalDate today = today();
        LocalDate existingDate = today.plusDays(2);

        EmployeeDailyScheduleEntity existing = new EmployeeDailyScheduleEntity();
        existing.setEmployee(employee);
        existing.setWorkingDate(existingDate);
        existing.setWorkingDay(true);
        existing.setOpenTime(LocalTime.of(12, 0));
        existing.setCloseTime(LocalTime.of(18, 0));

        when(employeeDailyScheduleRepository.deleteByWorkingDateBefore(today)).thenReturn(0L);
        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(employee));
        when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.of(salon));
        when(employeeDailyScheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                List.of(employee.getId()), today, today.plusDays(6)))
                .thenReturn(List.of(existing));
        List<EmployeeDailyScheduleEntity> saved = captureSavedSchedules();

        scheduler.ensureNext7DaysSchedules();

        assertEquals(6, saved.size());
        assertFalse(saved.stream().anyMatch(item -> existingDate.equals(item.getWorkingDate())));
        assertEquals(1, saved.stream().filter(item -> today.equals(item.getWorkingDate())).count());
        assertTrue(saved.stream()
                .noneMatch(item -> item.getOpenTime() != null && item.getOpenTime().equals(LocalTime.of(12, 0))));
    }

    @Test
    void ensureNext7DaysSchedulesDeletesPastSchedulesEvenWhenNoEligibleEmployees() {
        LocalDate today = today();

        when(employeeDailyScheduleRepository.deleteByWorkingDateBefore(today)).thenReturn(4L);
        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc()).thenReturn(List.of());

        scheduler.ensureNext7DaysSchedules();

        verify(employeeDailyScheduleRepository).deleteByWorkingDateBefore(today);
        verify(employeeDailyScheduleRepository, never()).saveAll(any());
    }

    @Test
    void ensureNext7DaysSchedulesUsesConfiguredBookingTimezone() {
        TimeZone originalDefault = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            bookingProperties.setTimezone("Pacific/Kiritimati");

            EmployeeEntity employee = employee(true);
            HairSalonEntity salon = salonWithWeekdayHours(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
            LocalDate configuredToday = today();
            LocalDate serverDefaultToday = LocalDate.now();
            assertNotEquals(serverDefaultToday, configuredToday);

            when(employeeDailyScheduleRepository.deleteByWorkingDateBefore(configuredToday)).thenReturn(0L);
            when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(employee));
            when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.of(salon));
            when(employeeDailyScheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                    List.of(employee.getId()), configuredToday, configuredToday.plusDays(6)))
                    .thenReturn(List.of());
            List<EmployeeDailyScheduleEntity> saved = captureSavedSchedules();

            scheduler.ensureNext7DaysSchedules();

            verify(employeeDailyScheduleRepository).deleteByWorkingDateBefore(configuredToday);
            assertEquals(configuredToday, saved.get(0).getWorkingDate());
            assertEquals(configuredToday.plusDays(6), saved.get(6).getWorkingDate());
        } finally {
            TimeZone.setDefault(originalDefault);
        }
    }

    private EmployeeEntity employee(boolean bookable) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(UUID.randomUUID());
        employee.setActive(true);
        employee.setBookable(bookable);
        return employee;
    }

    private HairSalonEntity salonWithWeekdayHours(boolean workingDay, LocalTime open, LocalTime close) {
        HairSalonEntity salon = new HairSalonEntity();
        salon.setId(hairSalonProperties.getId());
        salon.setWorkingHours(List.of(
                dayHours(DayOfWeek.MONDAY, workingDay, open, close),
                dayHours(DayOfWeek.TUESDAY, workingDay, open, close),
                dayHours(DayOfWeek.WEDNESDAY, workingDay, open, close),
                dayHours(DayOfWeek.THURSDAY, workingDay, open, close),
                dayHours(DayOfWeek.FRIDAY, workingDay, open, close),
                dayHours(DayOfWeek.SATURDAY, workingDay, open, close),
                dayHours(DayOfWeek.SUNDAY, workingDay, open, close)));
        return salon;
    }

    private HairSalonHoursEntity dayHours(DayOfWeek day, boolean workingDay, LocalTime open, LocalTime close) {
        HairSalonHoursEntity entity = new HairSalonHoursEntity();
        entity.setDayOfWeek(day);
        entity.setWorkingDay(workingDay);
        entity.setOpenTime(workingDay ? open : null);
        entity.setCloseTime(workingDay ? close : null);
        return entity;
    }

    private List<EmployeeDailyScheduleEntity> captureSavedSchedules() {
        List<EmployeeDailyScheduleEntity> saved = new ArrayList<>();
        when(employeeDailyScheduleRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<EmployeeDailyScheduleEntity> schedules = invocation.getArgument(0);
            saved.addAll(toList(schedules));
            return saved;
        });
        return saved;
    }

    private List<EmployeeDailyScheduleEntity> toList(Iterable<EmployeeDailyScheduleEntity> iterable) {
        return iterable instanceof List<EmployeeDailyScheduleEntity> list ? list
                : java.util.stream.StreamSupport
                        .stream(iterable.spliterator(), false)
                        .toList();
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(bookingProperties.getTimezone()));
    }
}
