package com.booking.engine.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Emits structured, machine-readable security audit records.
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditLogger {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final JsonMapper jsonMapper;
    private final ClientIpResolver clientIpResolver;

    public SecurityAuditEvent.SecurityAuditEventBuilder event(String eventType, String outcome) {
        HttpServletRequest request = currentRequest();
        return SecurityAuditEvent.builder()
                .eventType(eventType)
                .outcome(outcome)
                .actorUsername(resolveActorUsername())
                .clientIp(resolveClientIp(request))
                .method(request != null ? request.getMethod() : null)
                .path(request != null ? request.getRequestURI() : null);
    }

    public void log(SecurityAuditEvent event) {
        try {
            AUDIT_LOG.info("{}", jsonMapper.writeValueAsString(event.toLogFields()));
        } catch (Exception exception) {
            AUDIT_LOG.warn(
                    "Failed to serialize security audit eventType={} outcome={}",
                    event.getEventType(),
                    event.getOutcome());
        }
    }

    public String maskEmail(String email) {
        return SensitiveLogSanitizer.maskEmail(email);
    }

    public String hashValue(String value) {
        return SensitiveLogSanitizer.hashValue(value);
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String resolveActorUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
            return null;
        }

        return name;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return request == null ? null : clientIpResolver.resolve(request);
    }
}
