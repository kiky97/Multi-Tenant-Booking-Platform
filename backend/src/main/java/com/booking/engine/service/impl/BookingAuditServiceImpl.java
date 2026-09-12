package com.booking.engine.service.impl;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.service.BookingAuditService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link BookingAuditService}.
 * Provides booking audit related business operations.
 */
@Service
@RequiredArgsConstructor
public class BookingAuditServiceImpl implements BookingAuditService {
    // ---------------------- Services ----------------------

    private final SecurityAuditLogger securityAuditLogger;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void auditAdminBookingCreated(BookingEntity booking) {
        Map<String, Object> additionalFields = buildBookingAuditFields(booking);
        additionalFields.put("newStatus", booking.getStatus() != null ? booking.getStatus().name() : null);
        securityAuditLogger.log(securityAuditLogger.event("ADMIN_BOOKING_CREATE", "SUCCESS")
                .resourceType("BOOKING")
                .resourceId(booking.getId() != null ? booking.getId().toString() : null)
                .additionalFields(additionalFields)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void auditAdminBookingUpdated(BookingEntity booking, BookingStatus previousStatus) {
        Map<String, Object> additionalFields = buildBookingAuditFields(booking);
        additionalFields.put("oldStatus", previousStatus != null ? previousStatus.name() : null);
        additionalFields.put("newStatus", booking.getStatus() != null ? booking.getStatus().name() : null);
        securityAuditLogger.log(securityAuditLogger.event("ADMIN_BOOKING_UPDATE", "SUCCESS")
                .resourceType("BOOKING")
                .resourceId(booking.getId() != null ? booking.getId().toString() : null)
                .additionalFields(additionalFields)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void auditAdminBookingCancelled(BookingEntity booking, BookingStatus previousStatus) {
        Map<String, Object> additionalFields = buildBookingAuditFields(booking);
        additionalFields.put("oldStatus", previousStatus != null ? previousStatus.name() : null);
        additionalFields.put("newStatus", booking.getStatus() != null ? booking.getStatus().name() : null);
        additionalFields.put("slotLocked", booking.getSlotLocked());
        securityAuditLogger.log(securityAuditLogger.event("ADMIN_BOOKING_CANCEL", "SUCCESS")
                .resourceType("BOOKING")
                .resourceId(booking.getId() != null ? booking.getId().toString() : null)
                .additionalFields(additionalFields)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String maskCustomerEmailForLogs(String email) {
        return securityAuditLogger.maskEmail(normalizeOptionalText(email));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String hashSearchForLogs(String search) {
        return securityAuditLogger.hashValue(normalizeOptionalText(search));
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Builds audit fields that describe the current booking state for admin
     * actions.
     */
    private Map<String, Object> buildBookingAuditFields(BookingEntity booking) {
        Map<String, Object> additionalFields = new LinkedHashMap<>();
        additionalFields.put("employeeId", booking.getEmployee() != null && booking.getEmployee().getId() != null
                ? booking.getEmployee().getId().toString()
                : null);
        additionalFields.put("treatmentId", booking.getTreatment() != null && booking.getTreatment().getId() != null
                ? booking.getTreatment().getId().toString()
                : null);
        additionalFields.put("bookingDate", booking.getBookingDate());
        additionalFields.put("startTime", booking.getStartTime());
        additionalFields.put("endTime", booking.getEndTime());
        additionalFields.put("customerEmailMask", securityAuditLogger.maskEmail(booking.getCustomerEmail()));
        additionalFields.put(
                "customerPhoneHash",
                securityAuditLogger.hashValue(normalizeAuditPhone(booking.getCustomerPhone())));
        return additionalFields;
    }

    /*
     * Normalizes a phone value before writing it to audit logs.
     */
    private String normalizeAuditPhone(String phone) {
        String normalized = normalizeOptionalText(phone);
        if (normalized == null) {
            return null;
        }

        String digitsOnly = normalized.replaceAll("[^0-9]", "");
        return digitsOnly.isBlank() ? null : digitsOnly;
    }

    /*
     * Normalizes optional text by trimming blank values to null.
     */
    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
