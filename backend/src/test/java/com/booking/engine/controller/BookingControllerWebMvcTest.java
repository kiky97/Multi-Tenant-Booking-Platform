package com.booking.engine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.PublicBookingHoldResponseDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.PublicBookingSummaryResponseDto;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.exception.GlobalExceptionHandler;
import com.booking.engine.security.ClientIpResolver;
import com.booking.engine.security.PublicBookingActionRateLimitService;
import com.booking.engine.security.PublicBookingActionRateLimitService.Action;
import com.booking.engine.service.PublicBookingService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Web integration tests for {@link BookingController}.
 */
class BookingControllerWebMvcTest {

    private static final String HOLD_ACCESS_TOKEN = "hold-token";

    private MockMvc mockMvc;

    @Mock
    private PublicBookingService bookingService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private PublicBookingActionRateLimitService actionRateLimitService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        BookingController controller = new BookingController(bookingService, clientIpResolver, actionRateLimitService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createBookingShouldReturn201WhenPayloadIsValid() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingResponseDto response = BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .stripePaymentIntentId("pi_test")
                .build();

        when(bookingService.create(any(BookingRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/public/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.stripePaymentIntentId").value("pi_test"));
    }

    @Test
    void createBookingShouldReturn400WhenPaymentMethodIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/public/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJsonWithoutPaymentMethod()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.paymentMethodId").exists());
    }

    @Test
    void holdBookingShouldReturn201WhenPayloadIsValid() throws Exception {
        UUID bookingId = UUID.randomUUID();
        PublicBookingHoldResponseDto response = PublicBookingHoldResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.PENDING)
                .holdAccessToken(HOLD_ACCESS_TOKEN)
                .build();

        when(bookingService.holdSlot(any(), any(), any())).thenReturn(response);
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");

        mockMvc.perform(post("/api/v1/public/bookings/hold")
                        .header("X-Booking-Device-Id", "device-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validHoldRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.holdAccessToken").value(HOLD_ACCESS_TOKEN))
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.customerEmail").doesNotExist())
                .andExpect(jsonPath("$.customerPhone").doesNotExist())
                .andExpect(jsonPath("$.stripePaymentIntentId").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    void prepareHeldBookingCheckoutShouldReturn200WhenPayloadIsValid() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingCheckoutSessionResponseDto response = BookingCheckoutSessionResponseDto.builder()
                .paymentIntentId("pi_checkout")
                .clientSecret("pi_checkout_secret_123")
                .paymentStatus("succeeded")
                .build();

        when(bookingService.prepareHeldBookingCheckout(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/public/bookings/{id}/checkout", bookingId)
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCheckoutRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentIntentId").value("pi_checkout"))
                .andExpect(jsonPath("$.clientSecret").value("pi_checkout_secret_123"))
                .andExpect(jsonPath("$.paymentStatus").value("succeeded"));
    }

    @Test
    void validateHeldBookingCheckoutShouldReturn204WhenPayloadIsValid() throws Exception {
        mockMvc.perform(post("/api/v1/public/bookings/{id}/checkout/validate", UUID.randomUUID())
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCheckoutValidationRequestJson()))
                .andExpect(status().isNoContent());
    }

    @Test
    void validateHeldBookingCheckoutShouldReturn400WhenCustomerEmailMissing() throws Exception {
        mockMvc.perform(post("/api/v1/public/bookings/{id}/checkout/validate", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCheckoutValidationRequestJsonWithoutEmail()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['customer.email']").exists());
    }

    @Test
    void confirmHeldBookingShouldReturn400WhenPaymentIntentIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/public/bookings/{id}/confirm", UUID.randomUUID())
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidConfirmRequestJsonWithoutPaymentIntent()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.paymentIntentId").exists());
    }

    @Test
    void getBookingByIdShouldReturn200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        PublicBookingSummaryResponseDto response = PublicBookingSummaryResponseDto.builder()
                .id(bookingId)
                .employeeId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .treatmentId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .employeeName("Mary")
                .treatmentName("Haircut")
                .bookingDate(java.time.LocalDate.of(2030, 1, 15))
                .startTime(java.time.LocalTime.of(10, 0))
                .endTime(java.time.LocalTime.of(11, 0))
                .status(BookingStatus.PENDING)
                .build();

        when(bookingService.getBookingById(bookingId, HOLD_ACCESS_TOKEN)).thenReturn(response);

        mockMvc.perform(get("/api/v1/public/bookings/{id}", bookingId)
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.employeeName").value("Mary"))
                .andExpect(jsonPath("$.treatmentName").value("Haircut"))
                .andExpect(jsonPath("$.bookingDate").value("2030-01-15"))
                .andExpect(jsonPath("$.startTime").value("10:00:00"))
                .andExpect(jsonPath("$.endTime").value("11:00:00"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.customerEmail").doesNotExist())
                .andExpect(jsonPath("$.customerPhone").doesNotExist())
                .andExpect(jsonPath("$.stripePaymentIntentId").doesNotExist())
                .andExpect(jsonPath("$.stripePaymentStatus").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.paymentCapturedAt").doesNotExist())
                .andExpect(jsonPath("$.paymentReleasedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.holdAccessToken").doesNotExist());
    }

    @Test
    void confirmHeldBookingShouldReturn429WhenActionLimitExceeded() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
        doThrow(new RateLimitExceededException(
                "public booking action target rate limit exceeded",
                "Too many booking requests. Please wait a moment and try again."))
                .when(actionRateLimitService)
                .registerAttempt(Action.CONFIRM, "203.0.113.10", bookingId, "device-123");

        mockMvc.perform(post("/api/v1/public/bookings/{id}/confirm", bookingId)
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN)
                        .header("X-Booking-Device-Id", "device-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmRequestJson()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value(
                        "Too many booking requests. Please wait a moment and try again."));

        verifyNoInteractions(bookingService);
    }

    private String validRequestJson() {
        return """
                {
                  "employeeId": "11111111-1111-1111-1111-111111111111",
                  "treatmentId": "22222222-2222-2222-2222-222222222222",
                  "bookingDate": "2030-01-15",
                  "startTime": "10:00:00",
                  "endTime": "10:05:00",
                  "paymentMethodId": "pm_card_visa",
                  "customer": {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+353870000000"
                  }
                }
                """;
    }

    private String validHoldRequestJson() {
        return """
                {
                  "employeeId": "11111111-1111-1111-1111-111111111111",
                  "treatmentId": "22222222-2222-2222-2222-222222222222",
                  "bookingDate": "2030-01-15",
                  "startTime": "10:00:00",
                  "endTime": "11:00:00"
                }
                """;
    }

    private String invalidRequestJsonWithoutPaymentMethod() {
        return """
                {
                  "employeeId": "11111111-1111-1111-1111-111111111111",
                  "treatmentId": "22222222-2222-2222-2222-222222222222",
                  "bookingDate": "2030-01-15",
                  "startTime": "10:00:00",
                  "endTime": "10:05:00",
                  "paymentMethodId": " ",
                  "customer": {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+353870000000"
                  }
                }
                """;
    }

    private String validCheckoutRequestJson() {
        return """
                {
                  "customer": {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+353870000000"
                  },
                  "confirmationTokenId": "ctoken_123"
                }
                """;
    }

    private String invalidConfirmRequestJsonWithoutPaymentIntent() {
        return """
                {
                  "paymentIntentId": " "
                }
                """;
    }

    private String validConfirmRequestJson() {
        return """
                {
                  "paymentIntentId": "pi_checkout"
                }
                """;
    }

    private String validCheckoutValidationRequestJson() {
        return """
                {
                  "customer": {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+353870000000"
                  }
                }
                """;
    }

    private String invalidCheckoutValidationRequestJsonWithoutEmail() {
        return """
                {
                  "customer": {
                    "name": "John Doe",
                    "email": " ",
                    "phone": "+353870000000"
                  }
                }
                """;
    }
}
