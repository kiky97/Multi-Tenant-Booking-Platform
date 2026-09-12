package com.booking.engine.service.employee.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.EmployeeSchedulePeriodSettingsEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.service.EmployeeScheduleTargetResolver;
import com.booking.engine.service.impl.EmployeeScheduleTargetResolverImpl;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeScheduleTargetResolverTest {

    @Mock
    private EmployeeRepository employeeRepository;

    private EmployeeScheduleTargetResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EmployeeScheduleTargetResolverImpl(employeeRepository);
    }

    @Test
    void resolveEmployeeOrThrowReturnsRequestedEmployee() {
        UUID employeeId = UUID.randomUUID();
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThat(resolver.resolveEmployeeOrThrow(employeeId)).isSameAs(employee);
    }

    @Test
    void resolveEmployeeOrThrowThrowsWhenEmployeeIsMissing() {
        UUID employeeId = UUID.randomUUID();
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveEmployeeOrThrow(employeeId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void resolveTargetEmployeesReturnsSpecificEmployeeAndStoresItOnSettings() {
        UUID employeeId = UUID.randomUUID();
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);

        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 6))
                .endDate(LocalDate.of(2026, 4, 8))
                .employeeId(employeeId)
                .applyToAllEmployees(false)
                .build();
        EmployeeSchedulePeriodSettingsEntity settings = new EmployeeSchedulePeriodSettingsEntity();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThat(resolver.resolveTargetEmployees(request, settings))
                .isEqualTo(new LinkedHashMap<>(java.util.Map.of(employeeId, employee)));
        assertThat(settings.getTargetEmployee()).isSameAs(employee);
    }

    @Test
    void resolveTargetEmployeesReturnsAllActiveBookableEmployeesAndClearsSettingsTarget() {
        EmployeeEntity first = new EmployeeEntity();
        first.setId(UUID.randomUUID());
        EmployeeEntity second = new EmployeeEntity();
        second.setId(UUID.randomUUID());

        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 6))
                .endDate(LocalDate.of(2026, 4, 8))
                .applyToAllEmployees(true)
                .build();
        EmployeeSchedulePeriodSettingsEntity settings = new EmployeeSchedulePeriodSettingsEntity();
        settings.setTargetEmployee(new EmployeeEntity());

        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(first, second));

        assertThat(resolver.resolveTargetEmployees(request, settings))
                .containsExactly(
                        org.assertj.core.api.Assertions.entry(first.getId(), first),
                        org.assertj.core.api.Assertions.entry(second.getId(), second));
        assertThat(settings.getTargetEmployee()).isNull();
    }

    @Test
    void resolveTargetEmployeesThrowsWhenAllEmployeesRequestedButNoneExist() {
        EmployeeSchedulePeriodRequestDto request = EmployeeSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 6))
                .endDate(LocalDate.of(2026, 4, 8))
                .applyToAllEmployees(true)
                .build();

        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc()).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolveTargetEmployees(request, new EmployeeSchedulePeriodSettingsEntity()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No active employees found for period update.");
    }
}
