package com.booking.engine.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.AvailabilitySlotDto;
import com.booking.engine.service.AvailabilityService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityControllerTest {

    @Mock
    private AvailabilityService availabilityService;

    @InjectMocks
    private AvailabilityController controller;

    @Test
    void getAvailabilityDelegatesToService() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2030, 1, 15);
        List<AvailabilitySlotDto> slots = List.of(
                AvailabilitySlotDto.builder()
                        .startTime(LocalTime.of(10, 0))
                        .endTime(LocalTime.of(10, 45))
                        .available(true)
                        .status("AVAILABLE")
                        .build());

        when(availabilityService.getAvailability(employeeId, date, treatmentId)).thenReturn(slots);

        List<AvailabilitySlotDto> response = controller.getAvailability(employeeId, date, treatmentId);

        assertThat(response).isEqualTo(slots);
        verify(availabilityService).getAvailability(employeeId, date, treatmentId);
    }
}
