package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.TreatmentMapper;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.DisplayOrderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TreatmentServiceImpl}.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@ExtendWith(MockitoExtension.class)
class TreatmentServiceImplTest {

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private TreatmentMapper mapper;

    @Mock
    private DisplayOrderService displayOrderService;

    @InjectMocks
    private TreatmentServiceImpl service;

    @Test
    void createTreatmentSavesAndReturnsDto() {
        TreatmentRequestDto req = TreatmentRequestDto.builder()
                .name("Cut")
                .durationMinutes(30)
                .price(new BigDecimal("20"))
                .description("Clean finish with premium detailing.")
                .build();

        TreatmentEntity entity = new TreatmentEntity();
        TreatmentEntity saved = new TreatmentEntity();
        saved.setId(UUID.randomUUID());

        TreatmentResponseDto dto = TreatmentResponseDto.builder()
                .id(saved.getId())
                .build();

        when(displayOrderService.resolveDisplayOrder(req.getDisplayOrder(), treatmentRepository))
                .thenReturn(0);
        when(mapper.toEntity(req)).thenReturn(entity);
        when(treatmentRepository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(dto);

        TreatmentResponseDto result = service.createTreatment(req);

        assertEquals(dto, result);
        assertEquals(0, entity.getDisplayOrder());

        verify(displayOrderService).lockActiveOrderingScope(treatmentRepository);
        verify(displayOrderService).resolveDisplayOrder(req.getDisplayOrder(), treatmentRepository);
        verify(displayOrderService).shiftDisplayOrders(0, treatmentRepository);
        verify(treatmentRepository).save(entity);
    }

    @Test
    void getAllTreatmentsMapsList() {
        TreatmentEntity entity = new TreatmentEntity();
        TreatmentResponseDto dto = TreatmentResponseDto.builder()
                .id(UUID.randomUUID())
                .build();

        when(treatmentRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(entity));
        when(mapper.toDto(entity)).thenReturn(dto);

        List<TreatmentResponseDto> result = service.getAllTreatments();

        assertEquals(1, result.size());
        assertEquals(dto, result.get(0));
    }

    @Test
    void updateTreatmentNotFoundThrows() {
        UUID id = UUID.randomUUID();

        when(treatmentRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.updateTreatment(id, TreatmentRequestDto.builder()
                        .name("Cut")
                        .durationMinutes(30)
                        .price(new BigDecimal("20"))
                        .description("Clean finish with premium detailing.")
                        .build()));

        verify(displayOrderService).lockActiveOrderingScope(treatmentRepository);
    }

    @Test
    void removeTreatmentDeletes() {
        UUID id = UUID.randomUUID();

        TreatmentEntity entity = new TreatmentEntity();
        entity.setId(id);
        entity.setDisplayOrder(2);

        when(treatmentRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.of(entity));

        service.removeTreatment(id);

        verify(displayOrderService).lockActiveOrderingScope(treatmentRepository);
        verify(treatmentRepository).delete(entity);
        verify(displayOrderService).shiftOrdersAfterDelete(2, treatmentRepository);
    }
}
