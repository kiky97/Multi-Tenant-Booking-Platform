package com.booking.engine.mapper;

import com.booking.engine.dto.HairSalonHoursResponseDto;
import com.booking.engine.entity.HairSalonHoursEntity;
import org.mapstruct.Mapper;

/** Mapper for hair salon working-hours entity and DTOs. */
@Mapper(config = GlobalMapperConfig.class)
public interface HairSalonHoursMapper {

    HairSalonHoursResponseDto toDto(HairSalonHoursEntity entity);
}
