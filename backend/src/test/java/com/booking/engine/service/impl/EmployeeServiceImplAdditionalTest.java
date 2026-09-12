package com.booking.engine.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.EmployeeRequestDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.EmployeeMapper;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.service.DisplayOrderService;
import com.booking.engine.service.EmployeeBookingGuard;
import com.booking.engine.service.EmployeeScheduleSeedService;
import com.booking.engine.service.EmployeeTreatmentAssignmentService;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Additional unit tests for {@link EmployeeServiceImpl}.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeServiceImplAdditionalTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeMapper mapper;

    @Mock
    private DisplayOrderService displayOrderService;

    @Mock
    private EmployeeTreatmentAssignmentService employeeTreatmentAssignmentService;

    @Mock
    private EmployeeBookingGuard employeeBookingGuard;

    @Mock
    private EmployeeScheduleSeedService employeeScheduleSeedService;

    private EmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmployeeServiceImpl(
                employeeRepository,
                mapper,
                displayOrderService,
                employeeTreatmentAssignmentService,
                employeeBookingGuard,
                employeeScheduleSeedService);
    }

    @Test
    void getEmployeeByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findByIdAndActiveTrueWithTreatments(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEmployeeById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void createEmployeeValidatesDisplayOrderUpperBound() {
        EmployeeRequestDto request = EmployeeRequestDto.builder()
                .name("B")
                .role("Senior Employee")
                .displayOrder(5)
                .build();

        when(displayOrderService.resolveDisplayOrder(request.getDisplayOrder(), employeeRepository))
                .thenThrow(new IllegalArgumentException("Display order cannot be greater than 1"));

        assertThatThrownBy(() -> service.createEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display order cannot be greater");

        verify(displayOrderService).lockActiveOrderingScope(employeeRepository);
    }

    @Test
    void updateEmployeeShiftsDownWhenMovingForward() {
        UUID employeeId = UUID.randomUUID();
        Set<TreatmentEntity> requestedTreatments = Set.of();

        EmployeeEntity employee = new EmployeeEntity();
        employee.setActive(true);
        employee.setDisplayOrder(0);
        employee.setProvidedTreatments(new LinkedHashSet<>());

        when(employeeRepository.findByIdAndActiveTrueWithTreatments(employeeId)).thenReturn(Optional.of(employee));
        when(displayOrderService.resolveDisplayOrderForUpdate(2, 0, employeeRepository)).thenReturn(2);
        when(employeeTreatmentAssignmentService.resolveRequestedTreatments(null)).thenReturn(requestedTreatments);

        EmployeeRequestDto request = EmployeeRequestDto.builder()
                .role("Master Employee")
                .displayOrder(2)
                .build();

        service.updateEmployee(employeeId, request);

        assertThat(employee.getDisplayOrder()).isEqualTo(2);

        verify(displayOrderService).lockActiveOrderingScope(employeeRepository);
        verify(displayOrderService).resolveDisplayOrderForUpdate(2, 0, employeeRepository);
        verify(displayOrderService).moveDisplayOrder(0, 2, employeeRepository);
        verify(employeeBookingGuard).validateRemovedTreatmentsHaveNoFutureBookings(employee, requestedTreatments);
        verify(mapper).updateFromDto(request, employee);
        verify(employeeScheduleSeedService).seedUpcomingScheduleIfBookable(employee);
    }

    @Test
    void updateEmployeeShiftsUpWhenMovingBackward() {
        UUID employeeId = UUID.randomUUID();
        Set<TreatmentEntity> requestedTreatments = Set.of();

        EmployeeEntity employee = new EmployeeEntity();
        employee.setActive(true);
        employee.setDisplayOrder(2);
        employee.setProvidedTreatments(new LinkedHashSet<>());

        when(employeeRepository.findByIdAndActiveTrueWithTreatments(employeeId)).thenReturn(Optional.of(employee));
        when(displayOrderService.resolveDisplayOrderForUpdate(0, 2, employeeRepository)).thenReturn(0);
        when(employeeTreatmentAssignmentService.resolveRequestedTreatments(null)).thenReturn(requestedTreatments);

        EmployeeRequestDto request = EmployeeRequestDto.builder()
                .role("Master Employee")
                .displayOrder(0)
                .build();

        service.updateEmployee(employeeId, request);

        assertThat(employee.getDisplayOrder()).isEqualTo(0);

        verify(displayOrderService).lockActiveOrderingScope(employeeRepository);
        verify(displayOrderService).resolveDisplayOrderForUpdate(0, 2, employeeRepository);
        verify(displayOrderService).moveDisplayOrder(2, 0, employeeRepository);
        verify(employeeBookingGuard).validateRemovedTreatmentsHaveNoFutureBookings(employee, requestedTreatments);
        verify(mapper).updateFromDto(request, employee);
        verify(employeeScheduleSeedService).seedUpcomingScheduleIfBookable(employee);
    }

    @Test
    void updateEmployeeValidatesDisplayOrderUpperBoundForUpdate() {
        UUID employeeId = UUID.randomUUID();

        EmployeeEntity employee = new EmployeeEntity();
        employee.setActive(true);
        employee.setDisplayOrder(1);
        employee.setProvidedTreatments(new LinkedHashSet<>());

        when(employeeRepository.findByIdAndActiveTrueWithTreatments(employeeId)).thenReturn(Optional.of(employee));
        when(displayOrderService.resolveDisplayOrderForUpdate(5, 1, employeeRepository))
                .thenThrow(new IllegalArgumentException("Display order cannot be greater than 1"));

        EmployeeRequestDto request = EmployeeRequestDto.builder()
                .role("Master Employee")
                .displayOrder(5)
                .build();

        assertThatThrownBy(() -> service.updateEmployee(employeeId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display order cannot be greater");

        verify(displayOrderService).lockActiveOrderingScope(employeeRepository);
    }

    @Test
    void updateEmployeeUpdatesBookableFlagWhenProvided() {
        UUID employeeId = UUID.randomUUID();
        Set<TreatmentEntity> requestedTreatments = Set.of();

        EmployeeEntity employee = new EmployeeEntity();
        employee.setActive(true);
        employee.setDisplayOrder(1);
        employee.setBookable(false);
        employee.setProvidedTreatments(new LinkedHashSet<>());

        when(employeeRepository.findByIdAndActiveTrueWithTreatments(employeeId)).thenReturn(Optional.of(employee));
        when(displayOrderService.resolveDisplayOrderForUpdate(null, 1, employeeRepository)).thenReturn(1);
        when(employeeTreatmentAssignmentService.resolveRequestedTreatments(null)).thenReturn(requestedTreatments);

        EmployeeRequestDto request = EmployeeRequestDto.builder()
                .bookable(true)
                .build();

        service.updateEmployee(employeeId, request);

        assertThat(employee.getBookable()).isTrue();
        verify(employeeBookingGuard).validateRemovedTreatmentsHaveNoFutureBookings(employee, requestedTreatments);
        verify(mapper).updateFromDto(request, employee);
    }

    @Test
    void updateEmployeeLeavesBookableFlagUnchangedWhenNotProvided() {
        UUID employeeId = UUID.randomUUID();
        Set<TreatmentEntity> requestedTreatments = Set.of();

        EmployeeEntity employee = new EmployeeEntity();
        employee.setActive(true);
        employee.setDisplayOrder(1);
        employee.setBookable(false);
        employee.setProvidedTreatments(new LinkedHashSet<>());

        when(employeeRepository.findByIdAndActiveTrueWithTreatments(employeeId)).thenReturn(Optional.of(employee));
        when(displayOrderService.resolveDisplayOrderForUpdate(null, 1, employeeRepository)).thenReturn(1);
        when(employeeTreatmentAssignmentService.resolveRequestedTreatments(null)).thenReturn(requestedTreatments);

        service.updateEmployee(employeeId, EmployeeRequestDto.builder().build());

        assertThat(employee.getBookable()).isFalse();
        verify(employeeBookingGuard).validateRemovedTreatmentsHaveNoFutureBookings(employee, requestedTreatments);
    }

    @Test
    void reorderEmployeesSwapsDisplayOrdersUsingTemporaryOrder() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        EmployeeEntity first = new EmployeeEntity();
        first.setId(firstId);
        first.setActive(true);
        first.setDisplayOrder(0);

        EmployeeEntity second = new EmployeeEntity();
        second.setId(secondId);
        second.setActive(true);
        second.setDisplayOrder(1);

        when(employeeRepository.findByIdAndActiveTrue(firstId)).thenReturn(Optional.of(first));
        when(employeeRepository.findByIdAndActiveTrue(secondId)).thenReturn(Optional.of(second));
        when(displayOrderService.resolveTemporaryDisplayOrder(employeeRepository)).thenReturn(-1);

        service.reorderEmployees(firstId, secondId);

        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(second.getDisplayOrder()).isEqualTo(0);
        verify(employeeRepository, times(2)).saveAndFlush(first);
        verify(employeeRepository).saveAndFlush(second);
    }
}
