package com.booking.engine.service.employee.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.mapper.EmployeeScheduleMapper;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.service.EmployeeScheduleDayService;
import com.booking.engine.service.impl.EmployeeScheduleDayServiceImpl;
import com.booking.engine.service.impl.EmployeeScheduleTargetResolverImpl;
import com.booking.engine.service.impl.EmployeeScheduleValidationServiceImpl;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeScheduleDayServiceTest {

    @Mock
    private EmployeeDailyScheduleRepository scheduleRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeScheduleMapper mapper;

    private EmployeeScheduleDayService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeScheduleDayServiceImpl(
                scheduleRepository,
                mapper,
                new EmployeeScheduleValidationServiceImpl(),
                new EmployeeScheduleTargetResolverImpl(employeeRepository));
    }

    @Test
    void getScheduleReturnsContinuousRangeWithNonWorkingDefaultsForMissingDates() {
        UUID employeeId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 4, 6);
        LocalDate end = LocalDate.of(2026, 4, 8);

        EmployeeDailyScheduleEntity existing = new EmployeeDailyScheduleEntity();
        existing.setWorkingDate(start.plusDays(1));
        EmployeeScheduleResponseDto mapped = EmployeeScheduleResponseDto.builder()
                .workingDate(start.plusDays(1))
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .build();

        when(scheduleRepository.findByEmployeeIdAndWorkingDateBetweenOrderByWorkingDateAsc(employeeId, start, end))
                .thenReturn(List.of(existing));
        when(mapper.toDto(existing)).thenReturn(mapped);

        List<EmployeeScheduleResponseDto> result = service.getSchedule(employeeId, start, end);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).isWorkingDay()).isFalse();
        assertThat(result.get(0).getWorkingDate()).isEqualTo(start);
        assertThat(result.get(1)).isSameAs(mapped);
        assertThat(result.get(2).isWorkingDay()).isFalse();
        assertThat(result.get(2).getWorkingDate()).isEqualTo(end);
    }

    @Test
    void upsertDayCreatesNewEntityWhenScheduleDoesNotExist() {
        UUID employeeId = UUID.randomUUID();
        LocalDate workingDate = LocalDate.of(2026, 4, 23);

        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);

        EmployeeScheduleRequestDto request = EmployeeScheduleRequestDto.builder()
                .workingDate(workingDate)
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .breakStartTime(LocalTime.of(13, 0))
                .breakEndTime(LocalTime.of(14, 0))
                .build();

        EmployeeDailyScheduleEntity mapped = new EmployeeDailyScheduleEntity();
        mapped.setWorkingDate(workingDate);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(scheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, workingDate)).thenReturn(Optional.empty());
        when(mapper.toEntity(request)).thenReturn(mapped);

        service.upsertDay(employeeId, request);

        assertThat(mapped.getEmployee()).isSameAs(employee);
        assertThat(mapped.isWorkingDay()).isTrue();
        assertThat(mapped.getOpenTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(mapped.getBreakStartTime()).isEqualTo(LocalTime.of(13, 0));
        verify(scheduleRepository).save(mapped);
    }

    @Test
    void upsertDayUpdatesExistingEntityAndClearsTimesForNonWorkingDay() {
        UUID employeeId = UUID.randomUUID();
        LocalDate workingDate = LocalDate.of(2026, 4, 23);

        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);

        EmployeeDailyScheduleEntity existing = new EmployeeDailyScheduleEntity();
        existing.setWorkingDate(workingDate);
        existing.setWorkingDay(true);
        existing.setOpenTime(LocalTime.of(9, 0));
        existing.setCloseTime(LocalTime.of(18, 0));
        existing.setBreakStartTime(LocalTime.of(13, 0));
        existing.setBreakEndTime(LocalTime.of(14, 0));

        EmployeeScheduleRequestDto request = EmployeeScheduleRequestDto.builder()
                .workingDate(workingDate)
                .workingDay(false)
                .build();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(scheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, workingDate))
                .thenReturn(Optional.of(existing));

        service.upsertDay(employeeId, request);

        assertThat(existing.isWorkingDay()).isFalse();
        assertThat(existing.getOpenTime()).isNull();
        assertThat(existing.getCloseTime()).isNull();
        assertThat(existing.getBreakStartTime()).isNull();
        assertThat(existing.getBreakEndTime()).isNull();
        verify(scheduleRepository).save(existing);
    }

    @Test
    void upsertDayThrowsWhenEmployeeDoesNotExist() {
        UUID employeeId = UUID.randomUUID();
        EmployeeScheduleRequestDto request = EmployeeScheduleRequestDto.builder()
                .workingDate(LocalDate.of(2026, 4, 23))
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .build();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertDay(employeeId, request))
                .isInstanceOf(com.booking.engine.exception.EntityNotFoundException.class);
    }
}
