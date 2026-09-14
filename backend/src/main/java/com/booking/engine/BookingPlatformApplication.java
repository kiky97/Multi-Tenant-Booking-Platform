package com.booking.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for Salon Booking Platform Spring Boot application.
 * Enables scheduling and configuration properties binding.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class BookingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingPlatformApplication.class, args);
    }

}
