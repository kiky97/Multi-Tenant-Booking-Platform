package com.booking.engine.mapper;

import com.booking.engine.dto.EmployeeRequestDto;
import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.TreatmentEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Mapper for employee entity and DTOs. */
@Mapper(config = GlobalMapperConfig.class)
public interface EmployeeMapper extends BaseMapper<EmployeeEntity, EmployeeRequestDto, EmployeeResponseDto> {

    /** {@inheritDoc} */
    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "bookable", ignore = true)
    @Mapping(target = "providedTreatments", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    EmployeeEntity toEntity(EmployeeRequestDto request);

    @Override
    @Mapping(target = "treatmentIds", source = "providedTreatments")
    EmployeeResponseDto toDto(EmployeeEntity entity);

    @Override
    @InheritConfiguration(name = "toEntity")
    @Mapping(target = "bookable", ignore = true)
    @Mapping(target = "providedTreatments", ignore = true)
    void updateFromDto(EmployeeRequestDto request, @MappingTarget EmployeeEntity entity);

    default List<UUID> map(Set<TreatmentEntity> providedTreatments) {
        if (providedTreatments == null) {
            return List.of();
        }

        return providedTreatments.stream()
                .sorted(Comparator
                        .comparing(TreatmentEntity::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(TreatmentEntity::getId, Comparator.nullsLast(UUID::compareTo)))
                .map(TreatmentEntity::getId)
                .toList();
    }
}
