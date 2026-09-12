package com.booking.engine.service.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.booking.engine.repository.HairSalonRepository;
import com.booking.engine.service.EmployeeScheduleSeedService;
import com.booking.engine.service.impl.EmployeeScheduleSeedServiceImpl;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeScheduleSeedServiceTest {

    @Mock
    private EmployeeDailyScheduleRepository employeeDailyScheduleRepository;

    @Mock
    private HairSalonRepository hairSalonRepository;

    private EmployeeScheduleSeedService service;
    private HairSalonProperties hairSalonProperties;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");

        hairSalonProperties = new HairSalonProperties();
        hairSalonProperties.setId(UUID.randomUUID());

        service = new EmployeeScheduleSeedServiceImpl(
                employeeDailyScheduleRepository,
                hairSalonRepository,
                bookingProperties,
                hairSalonProperties);
    }

    @Test
    void seedUpcomingScheduleIfBookableCreatesMissingRowsUsingSalonDefaults() {
        EmployeeEntity employee = employee(true);
        HairSalonEntity salon = salonWithWeekdayHours(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));

        when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.of(salon));
        when(employeeDailyScheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                List.of(employee.getId()), today, today.plusDays(6)))
                .thenReturn(List.of());
        List<EmployeeDailyScheduleEntity> saved = captureSavedSchedules();

        service.seedUpcomingScheduleIfBookable(employee);

        assertEquals(7, saved.size());
        assertEquals(LocalTime.of(9, 0), saved.get(0).getOpenTime());
        assertEquals(LocalTime.of(17, 0), saved.get(0).getCloseTime());
    }

    @Test
    void seedUpcomingScheduleIfBookableDoesNotOverwriteExistingDates() {
        EmployeeEntity employee = employee(true);
        HairSalonEntity salon = salonWithWeekdayHours(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));
        LocalDate existingDate = today.plusDays(2);

        EmployeeDailyScheduleEntity existing = new EmployeeDailyScheduleEntity();
        existing.setEmployee(employee);
        existing.setWorkingDate(existingDate);
        existing.setWorkingDay(true);
        existing.setOpenTime(LocalTime.of(12, 0));
        existing.setCloseTime(LocalTime.of(18, 0));

        when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.of(salon));
        when(employeeDailyScheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                List.of(employee.getId()), today, today.plusDays(6)))
                .thenReturn(List.of(existing));
        List<EmployeeDailyScheduleEntity> saved = captureSavedSchedules();

        service.seedUpcomingScheduleIfBookable(employee);

        assertEquals(6, saved.size());
        assertFalse(saved.stream().anyMatch(item -> existingDate.equals(item.getWorkingDate())));
    }

    @Test
    void seedUpcomingScheduleIfBookableSkipsMissingSalonConfiguration() {
        EmployeeEntity employee = employee(true);
        when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.empty());

        service.seedUpcomingScheduleIfBookable(employee);

        verify(employeeDailyScheduleRepository, never()).saveAll(any());
    }

    @Test
    void seedUpcomingScheduleIfBookableSkipsNonBookableEmployees() {
        service.seedUpcomingScheduleIfBookable(employee(false));

        verify(employeeDailyScheduleRepository, never()).saveAll(any());
        verify(hairSalonRepository, never()).findById(any());
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
}
