package com.booking.engine.service;

/**
 * Service contract for admin bootstrap operations.
 * Defines admin bootstrap related business operations.
 */
public interface AdminBootstrapService {

    /**
     * Creates or verifies the bootstrap admin account when bootstrap is enabled.
     */
    void bootstrapIfEnabled();
}
