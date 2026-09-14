package com.booking.engine.platform.security;

import com.booking.engine.entity.UserRole;
import java.util.UUID;

public record PlatformPrincipal(UUID userId, UserRole role) {}
