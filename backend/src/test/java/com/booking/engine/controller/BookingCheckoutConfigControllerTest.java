package com.booking.engine.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.properties.StripeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookingCheckoutConfigControllerTest {

    private MockMvc mockMvc;
    private StripeProperties stripeProperties;

    @BeforeEach
    void setup() {
        stripeProperties = new StripeProperties();
        stripeProperties.setCurrency("eur");
        stripeProperties.setPublishableKey("pk_test_checkout");

        BookingCheckoutConfigController controller = new BookingCheckoutConfigController(stripeProperties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getCheckoutConfigReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/public/booking-checkout-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("eur"))
                .andExpect(jsonPath("$.stripePublishableKey").value("pk_test_checkout"));
    }
}
