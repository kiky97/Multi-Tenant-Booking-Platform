package com.booking.engine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingCheckoutValidationRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.PublicBookingHoldResponseDto;
import com.booking.engine.dto.PublicBookingSummaryResponseDto;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.security.ClientIpResolver;
import com.booking.engine.security.PublicBookingActionRateLimitService;
import com.booking.engine.security.PublicBookingActionRateLimitService.Action;
import com.booking.engine.service.PublicBookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookingControllerTest {

    private static final String HOLD_ACCESS_TOKEN = "hold-token";

    private MockMvc mockMvc;
    private PublicBookingService bookingService;
    private ClientIpResolver clientIpResolver;
    private PublicBookingActionRateLimitService actionRateLimitService;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        bookingService = org.mockito.Mockito.mock(PublicBookingService.class);
        clientIpResolver = org.mockito.Mockito.mock(ClientIpResolver.class);
        actionRateLimitService = org.mockito.Mockito.mock(PublicBookingActionRateLimitService.class);
        BookingController controller = new BookingController(bookingService, clientIpResolver, actionRateLimitService);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void getByIdReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.getBookingById(id, HOLD_ACCESS_TOKEN))
                .thenReturn(PublicBookingSummaryResponseDto.builder().id(id).build());
        mockMvc.perform(get("/api/v1/public/bookings/{id}", id)
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void createReturns201() throws Exception {
        BookingRequestDto req = BookingRequestDto.builder()
                .employeeId(UUID.randomUUID())
                .treatmentId(UUID.randomUUID())
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .paymentMethodId("pm")
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("n").email("e@e.com").phone("1").build())
                .build();
        when(bookingService.create(req)).thenReturn(BookingResponseDto.builder()
                .id(UUID.randomUUID()).status(BookingStatus.CONFIRMED).build());
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");

        mockMvc.perform(post("/api/v1/public/bookings")
                        .header("X-Booking-Device-Id", "device-123")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(actionRateLimitService).registerAttempt(
                Action.DIRECT_CREATE,
                "203.0.113.10",
                null,
                "device-123");
    }

    @Test
    void holdReturns201() throws Exception {
        BookingHoldRequestDto req = BookingHoldRequestDto.builder()
                .employeeId(UUID.randomUUID())
                .treatmentId(UUID.randomUUID())
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();

        when(bookingService.holdSlot(req, "203.0.113.10", "device-123")).thenReturn(PublicBookingHoldResponseDto.builder()
                .id(UUID.randomUUID()).status(BookingStatus.PENDING).build());
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");

        mockMvc.perform(post("/api/v1/public/bookings/hold")
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .header("X-Booking-Device-Id", "device-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verifyNoInteractions(actionRateLimitService);
    }

    @Test
    void confirmHoldReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingConfirmationRequestDto req = BookingConfirmationRequestDto.builder()
                .paymentIntentId("pi_auth")
                .build();

        when(bookingService.confirmHeldBooking(bookingId, req, HOLD_ACCESS_TOKEN)).thenReturn(BookingResponseDto.builder()
                .id(bookingId).status(BookingStatus.CONFIRMED).build());
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");

        mockMvc.perform(post("/api/v1/public/bookings/{id}/confirm", bookingId)
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(actionRateLimitService).registerAttempt(
                Action.CONFIRM,
                "203.0.113.10",
                bookingId,
                null);
    }

    @Test
    void prepareCheckoutReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingCheckoutSessionRequestDto req = BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("n")
                        .email("e@e.com")
                        .phone("1")
                        .build())
                .confirmationTokenId("ctoken_123")
                .build();

        when(bookingService.prepareHeldBookingCheckout(bookingId, req, HOLD_ACCESS_TOKEN))
                .thenReturn(BookingCheckoutSessionResponseDto.builder()
                        .paymentIntentId("pi_checkout")
                        .clientSecret("pi_checkout_secret")
                        .paymentStatus("succeeded")
                        .build());
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");

        mockMvc.perform(post("/api/v1/public/bookings/{id}/checkout", bookingId)
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(actionRateLimitService).registerAttempt(
                Action.CHECKOUT_PREPARE,
                "203.0.113.10",
                bookingId,
                null);
    }

    @Test
    void validateCheckoutReturns204() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingCheckoutValidationRequestDto req = BookingCheckoutValidationRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("n")
                        .email("e@e.com")
                        .phone("1")
                        .build())
                .build();
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");

        mockMvc.perform(post("/api/v1/public/bookings/{id}/checkout/validate", bookingId)
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(bookingService).validateHeldBookingCheckout(bookingId, req, HOLD_ACCESS_TOKEN);
        verify(actionRateLimitService).registerAttempt(
                Action.CHECKOUT_VALIDATE,
                "203.0.113.10",
                bookingId,
                null);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
        mockMvc.perform(delete("/api/v1/public/bookings/{id}", id)
                        .header("X-Booking-Hold-Access-Token", HOLD_ACCESS_TOKEN))
                .andExpect(status().isNoContent());
        verify(bookingService).cancelBooking(id, HOLD_ACCESS_TOKEN);
        verify(actionRateLimitService).registerAttempt(
                Action.CANCEL,
                "203.0.113.10",
                id,
                null);
    }
}
