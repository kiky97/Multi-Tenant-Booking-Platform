package com.booking.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main entry point for the multi-tenant booking platform.
 */
@SpringBootApplication(scanBasePackages = "com.booking.engine.platform")
@EntityScan(basePackages = "com.booking.engine.entity")
@EnableJpaRepositories(basePackages = "com.booking.engine.platform.repository")
@EnableCaching
public class BookingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingPlatformApplication.class, args);
    }

}
