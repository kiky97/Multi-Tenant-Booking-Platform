package com.booking.engine.platform.service;

import com.booking.engine.entity.AuditAction;
import com.booking.engine.entity.AuditLog;
import com.booking.engine.entity.Organization;
import com.booking.engine.platform.repository.AuditLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Single write path for {@link AuditLog} rows, so every call site records the same shape of
 * entry instead of each controller assembling the entity itself. */
@Service
public class AuditLogService {
    private final AuditLogRepository auditLogs;

    public AuditLogService(AuditLogRepository auditLogs) { this.auditLogs = auditLogs; }

    public void record(Organization organization, UUID actorUserId, AuditAction action, String targetType,
            UUID targetId, String detail) {
        AuditLog entry = new AuditLog();
        entry.setOrganization(organization);
        entry.setActorUserId(actorUserId);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetail(detail);
        auditLogs.save(entry);
    }
}
