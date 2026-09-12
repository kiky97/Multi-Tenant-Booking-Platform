package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.EmployeeRequestDto;
import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.mapper.EmployeeMapper;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.service.DisplayOrderService;
import com.booking.engine.service.EmployeeBookingGuard;
import com.booking.engine.service.EmployeeScheduleSeedService;
import com.booking.engine.service.EmployeeTreatmentAssignmentService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EmployeeServiceImpl}.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

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

    private EmployeeServiceImpl employeeService;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeServiceImpl(
                employeeRepository,
                mapper,
                displayOrderService,
                employeeTreatmentAssignmentService,
                employeeBookingGuard,
                employeeScheduleSeedService);
    }

    @Test
    void createEmployeeShouldCreateAndSeedUpcomingScheduleForBookableEmployee() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        EmployeeRequestDto request = EmployeeRequestDto.builder()
                .name("Employee One")
                .role("Senior Employee")
                .bio("Senior employee")
                .photoUrl("https://example.com/b1.jpg")
                .bookable(true)
                .treatmentIds(List.of(treatmentId))
                .build();

        EmployeeEntity toSave = new EmployeeEntity();
        EmployeeEntity saved = new EmployeeEntity();
        saved.setId(employeeId);
        saved.setDisplayOrder(0);
        saved.setBookable(true);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);

        EmployeeResponseDto response = EmployeeResponseDto.builder()
                .id(employeeId)
                .build();

        when(displayOrderService.resolveDisplayOrder(request.getDisplayOrder(), employeeRepository))
                .thenReturn(0);
        when(employeeTreatmentAssignmentService.resolveRequestedTreatments(request.getTreatmentIds()))
                .thenReturn(Set.of(treatment));
        when(mapper.toEntity(request)).thenReturn(toSave);
        when(employeeRepository.save(toSave)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(response);

        employeeService.createEmployee(request);

        assertEquals(Boolean.TRUE, toSave.getBookable());
        assertEquals(Set.of(treatment), toSave.getProvidedTreatments());
        verify(displayOrderService).lockActiveOrderingScope(employeeRepository);
        verify(displayOrderService).resolveDisplayOrder(request.getDisplayOrder(), employeeRepository);
        verify(displayOrderService).shiftDisplayOrders(0, employeeRepository);
        verify(employeeTreatmentAssignmentService).resolveRequestedTreatments(request.getTreatmentIds());
        verify(employeeRepository).save(toSave);
        verify(employeeScheduleSeedService).seedUpcomingScheduleIfBookable(saved);
    }

    @Test
    void getBookableEmployeesShouldReturnOnlyBookableEmployees() {
        EmployeeEntity first = new EmployeeEntity();
        EmployeeEntity second = new EmployeeEntity();
        EmployeeResponseDto firstDto = EmployeeResponseDto.builder().id(UUID.randomUUID()).build();
        EmployeeResponseDto secondDto = EmployeeResponseDto.builder().id(UUID.randomUUID()).build();

        when(employeeRepository.findAllBookableWithTreatmentsOrderByDisplayOrderAsc())
                .thenReturn(List.of(first, second));
        when(mapper.toDto(first)).thenReturn(firstDto);
        when(mapper.toDto(second)).thenReturn(secondDto);

        List<EmployeeResponseDto> result = employeeService.getBookableEmployees();

        assertEquals(List.of(firstDto, secondDto), result);
        verify(employeeRepository).findAllBookableWithTreatmentsOrderByDisplayOrderAsc();
    }

    @Test
    void deleteEmployeeShouldThrowWhenActiveBookingsExist() {
        UUID employeeId = UUID.randomUUID();
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setDisplayOrder(0);
        employee.setActive(true);

        when(employeeRepository.findByIdAndActiveTrue(employeeId)).thenReturn(Optional.of(employee));
        doThrow(new IllegalStateException("Cannot delete employee while active bookings exist"))
                .when(employeeBookingGuard).validateNoActiveBookings(employeeId);

        assertThrows(IllegalStateException.class, () -> employeeService.deleteEmployee(employeeId));

        verify(displayOrderService).lockActiveOrderingScope(employeeRepository);
        verify(employeeRepository, never()).delete(any());
    }

    @Test
    void deleteEmployeeShouldDeleteWhenNoActiveBookingsExist() {
        UUID employeeId = UUID.randomUUID();
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setDisplayOrder(0);
        employee.setActive(true);

        when(employeeRepository.findByIdAndActiveTrue(employeeId)).thenReturn(Optional.of(employee));

        employeeService.deleteEmployee(employeeId);

        verify(displayOrderService).lockActiveOrderingScope(employeeRepository);
        verify(employeeBookingGuard).validateNoActiveBookings(employeeId);
        verify(employeeRepository).delete(employee);
        verify(displayOrderService).shiftOrdersAfterDelete(0, employeeRepository);
    }
}
