package com.booking.engine.service.employee.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.EmployeeSchedulePeriodDayRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;
import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.EmployeeSchedulePeriodDaySettingsEntity;
import com.booking.engine.entity.EmployeeSchedulePeriodSettingsEntity;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.EmployeeSchedulePeriodDaySettingsRepository;
import com.booking.engine.repository.EmployeeSchedulePeriodSettingsRepository;
import com.booking.engine.service.EmployeeSchedulePeriodService;
import com.booking.engine.service.impl.EmployeeSchedulePeriodServiceImpl;
import com.booking.engine.service.impl.EmployeeScheduleTargetResolverImpl;
import com.booking.engine.service.impl.EmployeeScheduleValidationServiceImpl;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeSchedulePeriodServiceTest {

    private static final int PERIOD_SETTINGS_SINGLETON_ID = 1;

    @Mock
    private EmployeeDailyScheduleRepository scheduleRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeSchedulePeriodSettingsRepository periodSettingsRepository;

    @Mock
    private EmployeeSchedulePeriodDaySettingsRepository periodDaySettingsRepository;

    private EmployeeSchedulePeriodService service;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.of(2026, 4, 23);
        service = new EmployeeSchedulePeriodServiceImpl(
                scheduleRepository,
                periodSettingsRepository,
                periodDaySettingsRepository,
                new EmployeeScheduleValidationServiceImpl(),
                new EmployeeScheduleTargetResolverImpl(employeeRepository));
    }

    @Test
    void getPeriodSettingsBuildsSortedResponseFromPersistedSettings() {
        EmployeeEntity targetEmployee = new EmployeeEntity();
        targetEmployee.setId(UUID.randomUUID());

        EmployeeSchedulePeriodSettingsEntity settings = EmployeeSchedulePeriodSettingsEntity.builder()
                .id(PERIOD_SETTINGS_SINGLETON_ID)
                .targetEmployee(targetEmployee)
                .applyToAllEmployees(false)
                .startDate(today)
                .endDate(today.plusDays(7))
                .build();

        when(periodSettingsRepository.findById(PERIOD_SETTINGS_SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(periodDaySettingsRepository.findAll()).thenReturn(reversedPeriodDaySettingsEntities());

        EmployeeSchedulePeriodResponseDto response = service.getPeriodSettings();

        assertThat(response.getEmployeeId()).isEqualTo(targetEmployee.getId());
        assertThat(response.getApplyToAllEmployees()).isFalse();
        assertThat(response.getDays()).hasSize(7);
        assertThat(response.getDays().get(0).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(response.getDays().get(6).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    void upsertPeriodForSpecificEmployeeSavesEachDateInRangeAndUpdatesFormState() {
        UUID employeeId = UUID.randomUUID();
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);

        LocalDate start = LocalDate.of(2026, 4, 6);
        LocalDate end = LocalDate.of(2026, 4, 8);

        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(start)
                .endDate(end)
                .employeeId(employeeId)
                .applyToAllEmployees(false)
                .days(weeklyPeriodConfig())
                .build();

        EmployeeSchedulePeriodSettingsEntity settings = periodSettingsEntity();
        List<EmployeeSchedulePeriodDaySettingsEntity> daySettings = periodDaySettingsEntities();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(scheduleRepository.findByEmployeeIdInAndWorkingDateBetween(List.of(employeeId), start, end))
                .thenReturn(List.of());
        when(periodSettingsRepository.findById(PERIOD_SETTINGS_SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(periodDaySettingsRepository.findAll()).thenReturn(daySettings);
        List<EmployeeDailyScheduleEntity> saved = captureSavedSchedules();

        EmployeeSchedulePeriodResponseDto response = service.upsertPeriod(request);

        verify(periodSettingsRepository).save(settings);
        verify(periodDaySettingsRepository).saveAll(daySettings);

        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).isWorkingDay()).isTrue();
        assertThat(saved.get(0).getOpenTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.get(1).isWorkingDay()).isFalse();
        assertThat(saved.get(1).getOpenTime()).isNull();
        assertThat(settings.getTargetEmployee()).isSameAs(employee);
        assertThat(response.getEmployeeId()).isEqualTo(employeeId);
    }

    @Test
    void upsertPeriodForAllEmployeesSavesRowsForEachEmployeeInDisplayOrder() {
        EmployeeEntity firstEmployee = new EmployeeEntity();
        firstEmployee.setId(UUID.randomUUID());
        EmployeeEntity secondEmployee = new EmployeeEntity();
        secondEmployee.setId(UUID.randomUUID());

        LocalDate date = LocalDate.of(2026, 4, 6);

        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(date)
                .endDate(date)
                .applyToAllEmployees(true)
                .days(weeklyPeriodConfig())
                .build();

        EmployeeSchedulePeriodSettingsEntity settings = periodSettingsEntity();
        settings.setTargetEmployee(firstEmployee);

        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(firstEmployee, secondEmployee));
        when(scheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                List.of(firstEmployee.getId(), secondEmployee.getId()), date, date))
                .thenReturn(List.of());
        when(periodSettingsRepository.findById(PERIOD_SETTINGS_SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(periodDaySettingsRepository.findAll()).thenReturn(periodDaySettingsEntities());
        List<EmployeeDailyScheduleEntity> saved = captureSavedSchedules();

        EmployeeSchedulePeriodResponseDto response = service.upsertPeriod(request);

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getEmployee().getId()).isEqualTo(firstEmployee.getId());
        assertThat(saved.get(1).getEmployee().getId()).isEqualTo(secondEmployee.getId());
        assertThat(response.getApplyToAllEmployees()).isTrue();
        assertThat(response.getEmployeeId()).isNull();
        assertThat(settings.getTargetEmployee()).isNull();
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

    private EmployeeSchedulePeriodSettingsEntity periodSettingsEntity() {
        return EmployeeSchedulePeriodSettingsEntity.builder()
                .id(PERIOD_SETTINGS_SINGLETON_ID)
                .applyToAllEmployees(true)
                .startDate(today)
                .endDate(today)
                .build();
    }

    private List<EmployeeSchedulePeriodDaySettingsEntity> periodDaySettingsEntities() {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> EmployeeSchedulePeriodDaySettingsEntity.builder()
                        .dayOfWeek(day)
                        .workingDay(false)
                        .build())
                .toList();
    }

    private List<EmployeeSchedulePeriodDaySettingsEntity> reversedPeriodDaySettingsEntities() {
        List<EmployeeSchedulePeriodDaySettingsEntity> reversed = new java.util.ArrayList<>(periodDaySettingsEntities());
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private List<EmployeeDailyScheduleEntity> captureSavedSchedules() {
        List<EmployeeDailyScheduleEntity> saved = new ArrayList<>();
        when(scheduleRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<EmployeeDailyScheduleEntity> schedules = invocation.getArgument(0);
            schedules.forEach(saved::add);
            return saved;
        });
        return saved;
    }
}
