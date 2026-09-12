package com.booking.engine.mapper;

import org.mapstruct.InheritConfiguration;
import org.mapstruct.MappingTarget;

/**
 * Base mapper interface for entity-DTO conversions.
 *
 * @param <E>   Entity type
 * @param <Req> Request DTO type
 * @param <Res> Response DTO type
 */
public interface BaseMapper<E, Req, Res> {

    E toEntity(Req request);

    Res toDto(E entity);

    @InheritConfiguration(name = "toEntity")
    void updateFromDto(Req request, @MappingTarget E entity);
}
