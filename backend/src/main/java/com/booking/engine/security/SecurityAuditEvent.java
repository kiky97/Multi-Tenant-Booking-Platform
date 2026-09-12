package com.booking.engine.security;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable structured security audit event.
 */
@Getter
@Builder
public class SecurityAuditEvent {

    @Builder.Default
    private final Instant occurredAt = Instant.now();

    private final String eventType;
    private final String outcome;
    private final String actorUsername;
    private final String clientIp;
    private final String method;
    private final String path;
    private final String resourceType;
    private final String resourceId;
    private final String reasonCode;

    @Builder.Default
    private final Map<String, Object> additionalFields = Collections.emptyMap();

    public Map<String, Object> toLogFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        putIfPresent(fields, "occurredAt", occurredAt);
        putIfPresent(fields, "eventType", eventType);
        putIfPresent(fields, "outcome", outcome);
        putIfPresent(fields, "actorUsername", actorUsername);
        putIfPresent(fields, "clientIp", clientIp);
        putIfPresent(fields, "method", method);
        putIfPresent(fields, "path", path);
        putIfPresent(fields, "resourceType", resourceType);
        putIfPresent(fields, "resourceId", resourceId);
        putIfPresent(fields, "reasonCode", reasonCode);
        if (additionalFields != null) {
            additionalFields.forEach((key, value) -> putIfPresent(fields, key, value));
        }
        return fields;
    }

    private void putIfPresent(Map<String, Object> fields, String key, Object value) {
        if (value != null) {
            fields.put(key, value);
        }
    }
}
