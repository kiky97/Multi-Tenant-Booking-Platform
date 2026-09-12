package com.booking.engine.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves client IP address for rate limiting and audit purposes.
 * Forwarded headers are evaluated only for explicitly trusted reverse proxies.
 * Untrusted peers are always resolved to their direct remote address.
 */
@Component
public class ClientIpResolver {

    private static final int MAX_IP_LENGTH = 128;

    private final TrustedProxyMatcher trustedProxyMatcher;

    public ClientIpResolver(TrustedProxyMatcher trustedProxyMatcher) {
        this.trustedProxyMatcher = trustedProxyMatcher;
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String remoteAddr = cleanAddress(request.getRemoteAddr());
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "unknown";
        }

        if (!trustedProxyMatcher.isTrusted(remoteAddr)) {
            return truncate(remoteAddr);
        }

        String forwardedAddress = firstUntrustedForwardedAddress(request);
        return truncate(forwardedAddress != null ? forwardedAddress : remoteAddr);
    }

    private String firstUntrustedForwardedAddress(HttpServletRequest request) {
        List<String> forwardedAddresses = new ArrayList<>();
        forwardedAddresses.addAll(extractForwardedForAddresses(request.getHeaders("Forwarded")));
        forwardedAddresses.addAll(extractCommaSeparatedAddresses(request.getHeaders("X-Forwarded-For")));

        Collections.reverse(forwardedAddresses);
        for (String candidate : forwardedAddresses) {
            if (!trustedProxyMatcher.isIpLiteral(candidate)) {
                continue;
            }

            if (!trustedProxyMatcher.isTrusted(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private List<String> extractForwardedForAddresses(Enumeration<String> headers) {
        List<String> addresses = new ArrayList<>();
        if (headers == null) {
            return addresses;
        }

        while (headers.hasMoreElements()) {
            String headerValue = headers.nextElement();
            if (headerValue == null || headerValue.isBlank()) {
                continue;
            }

            for (String entry : headerValue.split(",")) {
                for (String directive : entry.split(";")) {
                    String trimmedDirective = directive.trim();
                    if (!trimmedDirective.regionMatches(true, 0, "for=", 0, 4)) {
                        continue;
                    }

                    String value = trimmedDirective.substring(4).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                        value = value.substring(1, value.length() - 1);
                    }

                    String cleaned = cleanAddress(value);
                    if (cleaned != null) {
                        addresses.add(cleaned);
                    }
                }
            }
        }
        return addresses;
    }

    private List<String> extractCommaSeparatedAddresses(Enumeration<String> headers) {
        List<String> addresses = new ArrayList<>();
        if (headers == null) {
            return addresses;
        }

        while (headers.hasMoreElements()) {
            String headerValue = headers.nextElement();
            if (headerValue == null || headerValue.isBlank()) {
                continue;
            }

            for (String candidate : headerValue.split(",")) {
                String cleaned = cleanAddress(candidate);
                if (cleaned != null) {
                    addresses.add(cleaned);
                }
            }
        }
        return addresses;
    }

    private String cleanAddress(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        if (cleaned.isBlank() || "unknown".equalsIgnoreCase(cleaned)) {
            return null;
        }

        if (cleaned.startsWith("[")) {
            int closingBracket = cleaned.indexOf(']');
            if (closingBracket > 0) {
                cleaned = cleaned.substring(1, closingBracket);
            }
        } else if (cleaned.chars().filter(ch -> ch == ':').count() == 1 && cleaned.contains(".")) {
            cleaned = cleaned.substring(0, cleaned.indexOf(':'));
        }

        int zoneSeparator = cleaned.indexOf('%');
        if (zoneSeparator >= 0) {
            cleaned = cleaned.substring(0, zoneSeparator);
        }

        return cleaned.isBlank() ? null : cleaned;
    }

    private String truncate(String value) {
        return value.length() <= MAX_IP_LENGTH ? value : value.substring(0, MAX_IP_LENGTH);
    }
}
