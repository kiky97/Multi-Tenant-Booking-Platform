package com.booking.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SalonBookingPlatformApplicationTests {

    @Test
    void applicationEntryPointShouldBeLoadable() {
        assertThat(SalonBookingPlatformApplication.class).isNotNull();
    }
}