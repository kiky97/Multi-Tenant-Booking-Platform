package com.booking.engine.mapper;

import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.PublicBookingSummaryResponseDto;
import com.booking.engine.entity.BookingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for Booking entity and DTOs.
 */
@Mapper(config = GlobalMapperConfig.class)
public interface BookingMapper extends BaseMapper<BookingEntity, BookingRequestDto, BookingResponseDto> {

    /** {@inheritDoc} */
    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employee", ignore = true)
    @Mapping(target = "treatment", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    @Mapping(target = "stripePaymentIntentId", ignore = true)
    @Mapping(target = "stripePaymentStatus", ignore = true)
    @Mapping(target = "holdAmount", ignore = true)
    @Mapping(target = "holdClientIp", ignore = true)
    @Mapping(target = "holdClientDeviceId", ignore = true)
    @Mapping(target = "holdAccessTokenHash", ignore = true)
    @Mapping(target = "paymentCapturedAt", ignore = true)
    @Mapping(target = "paymentReleasedAt", ignore = true)
    @Mapping(target = "slotLocked", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "customerName", source = "customer.name")
    @Mapping(target = "customerEmail", source = "customer.email")
    @Mapping(target = "customerPhone", source = "customer.phone")
    BookingEntity toEntity(BookingRequestDto request);

    /** {@inheritDoc} */
    @Override
    @Mapping(target = "employeeId", source = "employee.id")
    @Mapping(target = "employeeName", source = "employee.name")
    @Mapping(target = "treatmentId", source = "treatment.id")
    @Mapping(target = "treatmentName", source = "treatment.name")
    @Mapping(target = "customerName", source = "customerName")
    @Mapping(target = "customerEmail", source = "customerEmail")
    @Mapping(target = "customerPhone", source = "customerPhone")
    BookingResponseDto toDto(BookingEntity entity);

    /**
     * Maps a booking to the unauthenticated public status shape without customer
     * PII, Stripe identifiers, or internal timestamps.
     *
     * @param entity booking entity
     * @return public-safe booking summary
     */
    @Mapping(target = "employeeId", source = "employee.id")
    @Mapping(target = "employeeName", source = "employee.name")
    @Mapping(target = "treatmentId", source = "treatment.id")
    @Mapping(target = "treatmentName", source = "treatment.name")
    PublicBookingSummaryResponseDto toPublicSummaryDto(BookingEntity entity);
}
