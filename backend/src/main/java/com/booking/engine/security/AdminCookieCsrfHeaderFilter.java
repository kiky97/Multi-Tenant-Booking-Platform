package com.booking.engine.security;

import com.booking.engine.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Requires the per-session CSRF token on unsafe admin requests authenticated by
 * the admin cookie. The expected token is stored inside the signed JWT session
 * cookie, while the browser sends its copy in a non-simple header that requires
 * a CORS preflight from cross-site pages.
 */
public class AdminCookieCsrfHeaderFilter extends OncePerRequestFilter {

    public static final String ADMIN_CSRF_HEADER = "X-Admin-CSRF";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String ADMIN_API_PREFIX = "/api/v1/admin/";
    private static final String ERROR_FORBIDDEN = "Forbidden";
    private static final String ERROR_MESSAGE = "Invalid admin CSRF token.";

    private final AdminAuthCookieService adminAuthCookieService;
    private final JwtService jwtService;
    private final JsonMapper jsonMapper;

    public AdminCookieCsrfHeaderFilter(
            AdminAuthCookieService adminAuthCookieService,
            JwtService jwtService,
            JsonMapper jsonMapper) {
        this.adminAuthCookieService = adminAuthCookieService;
        this.jwtService = jwtService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String cookieToken = resolveCookieTokenRequiringCsrf(request);
        if (cookieToken != null) {
            String headerToken = request.getHeader(ADMIN_CSRF_HEADER);
            if (!isValidCsrfToken(cookieToken, headerToken)) {
                writeForbiddenResponse(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveCookieTokenRequiringCsrf(HttpServletRequest request) {
        if (isUnsafeMethod(request)
                && isAdminApiRequest(request)
                && !hasBearerToken(request)) {
            return adminAuthCookieService.resolveTokenFromCookie(request);
        }

        return null;
    }

    private boolean isValidCsrfToken(String cookieToken, String headerToken) {
        if (headerToken == null || headerToken.isBlank()) {
            return false;
        }

        try {
            String expectedToken = jwtService.extractCsrfToken(cookieToken);
            return expectedToken != null
                    && MessageDigest.isEqual(
                            expectedToken.getBytes(StandardCharsets.UTF_8),
                            headerToken.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isUnsafeMethod(HttpServletRequest request) {
        return !SAFE_METHODS.contains(request.getMethod());
    }

    private boolean isAdminApiRequest(HttpServletRequest request) {
        return getRequestPath(request).startsWith(ADMIN_API_PREFIX);
    }

    private boolean hasBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ");
    }

    private String getRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }

        return requestUri;
    }

    private void writeForbiddenResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                ERROR_FORBIDDEN,
                ERROR_MESSAGE,
                getRequestPath(request)));
    }
}
