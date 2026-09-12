package com.booking.engine.mapper;

import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.entity.HairSalonEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for HairSalon entity and DTOs.
 */
@Mapper(config = GlobalMapperConfig.class, uses = HairSalonHoursMapper.class)
public interface HairSalonMapper extends BaseMapper<HairSalonEntity, HairSalonRequestDto, HairSalonResponseDto> {

    /** {@inheritDoc} */
    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "workingHours", ignore = true)
    HairSalonEntity toEntity(HairSalonRequestDto request);

    /** {@inheritDoc} */
    @Override
    HairSalonResponseDto toDto(HairSalonEntity entity);
}
