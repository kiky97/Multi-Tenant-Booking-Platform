package com.booking.engine.config;

import com.booking.engine.service.AdminBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup entry point for the explicit admin bootstrap flow.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final AdminBootstrapService adminBootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        adminBootstrapService.bootstrapIfEnabled();
    }
}
