package com.booking.engine.service.employee.schedule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.booking.engine.dto.EmployeeSchedulePeriodDayRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.service.EmployeeScheduleValidationService;
import com.booking.engine.service.impl.EmployeeScheduleValidationServiceImpl;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmployeeScheduleValidationServiceTest {

    private EmployeeScheduleValidationService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeScheduleValidationServiceImpl();
    }

    @Test
    void validateWorkingHoursAllowsWorkingDayWithoutBreak() {
        assertDoesNotThrow(() -> service.validateWorkingHours(
                true,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null,
                null));
    }

    @Test
    void validateWorkingHoursThrowsWhenOnlyOneBreakBoundaryProvided() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service
                .validateWorkingHours(true, LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(13, 0), null));

        assertEquals("Break start and end times must both be provided, or both omitted.", exception.getMessage());
    }

    @Test
    void mapWeeklyConfigsThrowsWhenDayIsMissing() {
        List<EmployeeSchedulePeriodDayRequestDto> sixDays = Arrays.stream(DayOfWeek.values())
                .filter(day -> day != DayOfWeek.SUNDAY)
                .map(this::nonWorkingDay)
                .toList();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.mapWeeklyConfigs(sixDays));

        assertEquals("All seven weekday configurations must be provided.", exception.getMessage());
    }

    @Test
    void mapWeeklyConfigsThrowsWhenDayIsDuplicated() {
        List<EmployeeSchedulePeriodDayRequestDto> duplicatedDays = Arrays.stream(DayOfWeek.values())
                .map(this::nonWorkingDay)
                .collect(java.util.stream.Collectors.toList());
        duplicatedDays.set(6, nonWorkingDay(DayOfWeek.MONDAY));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.mapWeeklyConfigs(duplicatedDays));

        assertEquals("Duplicate weekday configuration: MONDAY", exception.getMessage());
    }

    @Test
    void mapWeeklyConfigsReturnsConfigsIndexedByDayOfWeek() {
        Map<DayOfWeek, EmployeeSchedulePeriodDayRequestDto> result = service.mapWeeklyConfigs(weeklyPeriodConfig());

        assertEquals(7, result.size());
        assertEquals(DayOfWeek.MONDAY, result.get(DayOfWeek.MONDAY).getDayOfWeek());
    }

    @Test
    void validatePeriodRequestThrowsWhenDateRangeIsInvalid() {
        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 10))
                .endDate(LocalDate.of(2026, 4, 6))
                .employeeId(UUID.randomUUID())
                .applyToAllEmployees(false)
                .days(weeklyPeriodConfig())
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.validatePeriodRequest(request));

        assertEquals("Start date must be earlier than or equal to end date.", exception.getMessage());
    }

    @Test
    void validatePeriodRequestThrowsWhenAllEmployeesAndSpecificEmployeeAreBothSelected() {
        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 6))
                .endDate(LocalDate.of(2026, 4, 10))
                .employeeId(UUID.randomUUID())
                .applyToAllEmployees(true)
                .days(weeklyPeriodConfig())
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.validatePeriodRequest(request));

        assertEquals("Choose either all employees or one employee for period update.", exception.getMessage());
    }

    private List<EmployeeSchedulePeriodDayRequestDto> weeklyPeriodConfig() {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> {
                    boolean workingDay = day == DayOfWeek.MONDAY;
                    return EmployeeSchedulePeriodDayRequestDto.builder()
                            .dayOfWeek(day)
                            .workingDay(workingDay)
                            .openTime(workingDay ? LocalTime.of(9, 0) : null)
                            .closeTime(workingDay ? LocalTime.of(18, 0) : null)
                            .breakStartTime(workingDay ? LocalTime.of(13, 0) : null)
                            .breakEndTime(workingDay ? LocalTime.of(14, 0) : null)
                            .build();
                })
                .toList();
    }

    private EmployeeSchedulePeriodDayRequestDto nonWorkingDay(DayOfWeek day) {
        return EmployeeSchedulePeriodDayRequestDto.builder()
                .dayOfWeek(day)
                .workingDay(false)
                .build();
    }
}
