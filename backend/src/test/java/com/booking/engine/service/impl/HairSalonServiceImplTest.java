package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.HairSalonMapper;
import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.repository.HairSalonRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HairSalonServiceImplTest {

    @Mock
    private HairSalonProperties props;
    @Mock
    private HairSalonRepository repo;
    @Mock
    private HairSalonMapper mapper;

    @InjectMocks
    private HairSalonServiceImpl service;

    @Test
    void getHairSalonReturnsDto() {
        UUID id = UUID.randomUUID();
        when(props.getId()).thenReturn(id);
        HairSalonEntity entity = new HairSalonEntity();
        HairSalonResponseDto dto = HairSalonResponseDto.builder().id(id).build();
        when(repo.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(dto);

        HairSalonResponseDto result = service.getHairSalonData();
        assertEquals(dto, result);
    }

    @Test
    void getHairSalonNotFoundThrows() {
        UUID id = UUID.randomUUID();
        when(props.getId()).thenReturn(id);
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.getHairSalonData());
    }

    @Test
    void updateHairSalonUpdatesEntity() {
        UUID id = UUID.randomUUID();
        when(props.getId()).thenReturn(id);
        HairSalonEntity entity = new HairSalonEntity();
        HairSalonRequestDto req = HairSalonRequestDto.builder().name("New").build();
        when(repo.findById(id)).thenReturn(Optional.of(entity));

        service.updateHairSalonData(req);

        verify(mapper).updateFromDto(req, entity);
    }
}
