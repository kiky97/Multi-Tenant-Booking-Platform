package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.HairSalonHoursRequestDto;
import com.booking.engine.dto.HairSalonHoursResponseDto;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.entity.HairSalonHoursEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.HairSalonHoursMapper;
import com.booking.engine.repository.HairSalonHoursRepository;
import com.booking.engine.repository.HairSalonRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link HairSalonHoursServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class HairSalonHoursServiceImplTest {

    @Mock
    private HairSalonRepository hairSalonRepository;

    @Mock
    private HairSalonHoursRepository hoursRepository;

    @Mock
    private HairSalonHoursMapper mapper;

    @InjectMocks
    private HairSalonHoursServiceImpl service;

    @Test
    void getWorkingHoursShouldReturnMappedList() {
        UUID salonId = UUID.randomUUID();
        HairSalonEntity salon = new HairSalonEntity();
        HairSalonHoursEntity h1 = new HairSalonHoursEntity();
        HairSalonHoursEntity h2 = new HairSalonHoursEntity();
        salon.setWorkingHours(List.of(h1, h2));

        when(hairSalonRepository.findById(salonId)).thenReturn(Optional.of(salon));
        when(mapper.toDto(h1)).thenReturn(HairSalonHoursResponseDto.builder().dayOfWeek(DayOfWeek.MONDAY).build());
        when(mapper.toDto(h2)).thenReturn(HairSalonHoursResponseDto.builder().dayOfWeek(DayOfWeek.TUESDAY).build());

        List<HairSalonHoursResponseDto> result = service.getWorkingHours(salonId);
        assertEquals(2, result.size());
    }

    @Test
    void updateWorkingDayShouldMarkAsNonWorking() {
        UUID salonId = UUID.randomUUID();
        HairSalonHoursEntity hours = new HairSalonHoursEntity();
        hours.setWorkingDay(true);
        hours.setOpenTime(LocalTime.of(9, 0));
        hours.setCloseTime(LocalTime.of(18, 0));

        HairSalonHoursRequestDto request = HairSalonHoursRequestDto.builder()
                .workingDay(false)
                .build();

        when(hoursRepository.findByHairSalonIdAndDayOfWeek(salonId, DayOfWeek.MONDAY)).thenReturn(Optional.of(hours));

        service.updateWorkingDay(salonId, DayOfWeek.MONDAY, request);

        assertEquals(false, hours.isWorkingDay());
    }

    @Test
    void updateWorkingDayShouldThrowWhenInvalidTimes() {
        UUID salonId = UUID.randomUUID();
        HairSalonHoursEntity hours = new HairSalonHoursEntity();

        HairSalonHoursRequestDto request = HairSalonHoursRequestDto.builder()
                .workingDay(true)
                .openTime(LocalTime.of(12, 0))
                .closeTime(LocalTime.of(11, 0))
                .build();

        when(hoursRepository.findByHairSalonIdAndDayOfWeek(salonId, DayOfWeek.MONDAY)).thenReturn(Optional.of(hours));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateWorkingDay(salonId, DayOfWeek.MONDAY, request));
    }

    @Test
    void updateWorkingDayShouldSetWorkingTimes() {
        UUID salonId = UUID.randomUUID();
        HairSalonHoursEntity hours = new HairSalonHoursEntity();

        HairSalonHoursRequestDto request = HairSalonHoursRequestDto.builder()
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(17, 0))
                .build();

        when(hoursRepository.findByHairSalonIdAndDayOfWeek(salonId, DayOfWeek.FRIDAY))
                .thenReturn(Optional.of(hours));

        service.updateWorkingDay(salonId, DayOfWeek.FRIDAY, request);

        assertEquals(true, hours.isWorkingDay());
        assertEquals(LocalTime.of(9, 0), hours.getOpenTime());
        assertEquals(LocalTime.of(17, 0), hours.getCloseTime());
    }

    @Test
    void getWorkingHoursShouldThrowWhenSalonNotFound() {
        UUID salonId = UUID.randomUUID();
        when(hairSalonRepository.findById(salonId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.getWorkingHours(salonId));
    }
}
