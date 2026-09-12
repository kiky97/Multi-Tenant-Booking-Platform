package com.booking.engine.service;

import com.booking.engine.dto.LoginRequestDto;
import com.booking.engine.dto.LoginResponseDto;

/**
 * Service contract for auth operations.
 * Defines auth related business operations.
 */
public interface AuthService {

    /**
     * Authenticates admin user and issues JWT access token.
     *
     * @param request login credentials
     * @return access token response
     */
    LoginResponseDto login(LoginRequestDto request);

    /**
     * Invalidates the current admin access-token version so already-issued access
     * tokens can no longer be used.
     *
     * @param username authenticated admin username
     */
    void logout(String username);
}
