package com.booking.engine.mapper;

import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.entity.Service;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Mapper for treatment entity and DTOs. */
@Mapper(config = GlobalMapperConfig.class)
public interface TreatmentMapper extends BaseMapper<Service, TreatmentRequestDto, TreatmentResponseDto> {

    /** {@inheritDoc} */
    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Service toEntity(TreatmentRequestDto request);
}
