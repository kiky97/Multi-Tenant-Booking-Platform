package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.PublicBookingHoldResponseDto;
import com.booking.engine.dto.PublicBookingSummaryResponseDto;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import org.springframework.stereotype.Component;

/**
 * Maps temporary slot holds into booking response DTOs.
 */
@Component
public class BookingHoldResponseMapper {

    // ---------------------- Public Methods ----------------------

    /*
     * Converts a temporary slot hold into the compact DTO shape already expected
     * by the existing frontend hold flows.
     */
    BookingResponseDto toHoldResponseDto(SlotHoldEntity slotHold) {
        return BookingResponseDto.builder()
                .id(slotHold.getId())
                .employeeId(slotHold.getEmployee().getId())
                .employeeName(slotHold.getEmployee().getName())
                .treatmentId(slotHold.getTreatment().getId())
                .treatmentName(slotHold.getTreatment().getName())
                .bookingDate(slotHold.getBookingDate())
                .startTime(slotHold.getStartTime())
                .endTime(slotHold.getEndTime())
                .customerName(slotHold.getCustomerName())
                .customerEmail(slotHold.getCustomerEmail())
                .customerPhone(slotHold.getCustomerPhone())
                .status(BookingStatus.PENDING)
                .expiresAt(slotHold.getExpiresAt())
                .stripePaymentIntentId(slotHold.getStripePaymentIntentId())
                .stripePaymentStatus(slotHold.getStripePaymentStatus())
                .holdAmount(slotHold.getHoldAmount())
                .paymentCapturedAt(slotHold.getPaymentCapturedAt())
                .paymentReleasedAt(slotHold.getPaymentReleasedAt())
                .createdAt(slotHold.getCreatedAt())
                .updatedAt(slotHold.getUpdatedAt())
                .build();
    }

    /*
     * Converts a public slot hold into the safe shape returned when the hold is
     * first created. The raw access token is never read back from persistence.
     */
    PublicBookingHoldResponseDto toPublicHoldResponseDto(SlotHoldEntity slotHold, String holdAccessToken) {
        return PublicBookingHoldResponseDto.builder()
                .id(slotHold.getId())
                .employeeId(slotHold.getEmployee().getId())
                .employeeName(slotHold.getEmployee().getName())
                .treatmentId(slotHold.getTreatment().getId())
                .treatmentName(slotHold.getTreatment().getName())
                .bookingDate(slotHold.getBookingDate())
                .startTime(slotHold.getStartTime())
                .endTime(slotHold.getEndTime())
                .status(BookingStatus.PENDING)
                .expiresAt(slotHold.getExpiresAt())
                .holdAccessToken(holdAccessToken)
                .build();
    }

    /*
     * Converts a public slot hold into the read-only status shape used by public
     * polling without exposing customer or payment fields.
     */
    PublicBookingSummaryResponseDto toPublicSummaryDto(SlotHoldEntity slotHold) {
        return PublicBookingSummaryResponseDto.builder()
                .id(slotHold.getId())
                .employeeId(slotHold.getEmployee().getId())
                .employeeName(slotHold.getEmployee().getName())
                .treatmentId(slotHold.getTreatment().getId())
                .treatmentName(slotHold.getTreatment().getName())
                .bookingDate(slotHold.getBookingDate())
                .startTime(slotHold.getStartTime())
                .endTime(slotHold.getEndTime())
                .status(BookingStatus.PENDING)
                .build();
    }
}
