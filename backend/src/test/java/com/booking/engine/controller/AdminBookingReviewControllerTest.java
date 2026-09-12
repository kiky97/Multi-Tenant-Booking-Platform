package com.booking.engine.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryResponseDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.service.AdminBookingService;
import com.booking.engine.service.BookingBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminBookingReviewControllerTest {

    private static final String ADMIN_HOLD_SESSION_HEADER = "X-Admin-Hold-Session-Id";

    private MockMvc mockMvc;
    private AdminBookingService bookingService;
    private BookingBlacklistService bookingBlacklistService;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        bookingService = org.mockito.Mockito.mock(AdminBookingService.class);
        bookingBlacklistService = org.mockito.Mockito.mock(BookingBlacklistService.class);
        AdminBookingReviewController controller = new AdminBookingReviewController(
                bookingService,
                bookingBlacklistService);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void getAdminBookingsReturns200() throws Exception {
        when(bookingService.getAdminBookings("john")).thenReturn(AdminBookingListResponseDto.builder()
                .confirmedCount(4)
                .filteredCount(1)
                .bookings(List.of(
                        BookingResponseDto.builder()
                                .id(UUID.randomUUID())
                                .customerName("John Doe")
                                .customerPhone("+353831234567")
                                .customerEmail("john@example.com")
                                .stripePaymentIntentId("pi_admin_visible")
                                .status(BookingStatus.CONFIRMED)
                                .build()))
                .build());

        mockMvc.perform(get("/api/v1/admin/bookings").param("search", "john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(4))
                .andExpect(jsonPath("$.filteredCount").value(1))
                .andExpect(jsonPath("$.bookings[0].customerName").value("John Doe"))
                .andExpect(jsonPath("$.bookings[0].customerPhone").value("+353831234567"))
                .andExpect(jsonPath("$.bookings[0].customerEmail").value("john@example.com"))
                .andExpect(jsonPath("$.bookings[0].stripePaymentIntentId").value("pi_admin_visible"));
    }

    @Test
    void createAdminBookingReturns200() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String adminHoldSessionId = "admin-session-1";
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .customerEmail("phone@example.com")
                .build();

        when(bookingService.createAdminBooking(request, adminHoldSessionId)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .customerEmail("phone@example.com")
                .build());

        mockMvc.perform(post("/api/v1/admin/bookings")
                        .header(ADMIN_HOLD_SESSION_HEADER, adminHoldSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerName").value("Phone Client"))
                .andExpect(jsonPath("$.customerPhone").value("+353831234567"))
                .andExpect(jsonPath("$.customerEmail").value("phone@example.com"));

        verify(bookingService).createAdminBooking(request, adminHoldSessionId);
    }

    @Test
    void holdAdminSlotReturns200() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String adminHoldSessionId = "admin-session-1";
        BookingHoldRequestDto request = BookingHoldRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();

        when(bookingService.holdAdminSlot(request, adminHoldSessionId)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/v1/admin/bookings/hold")
                        .header(ADMIN_HOLD_SESSION_HEADER, adminHoldSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(bookingService).holdAdminSlot(request, adminHoldSessionId);
    }

    @Test
    void refreshAdminHoldReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String adminHoldSessionId = "admin-session-1";

        when(bookingService.refreshAdminHold(bookingId, adminHoldSessionId)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/v1/admin/bookings/{id}/hold-refresh", bookingId)
                        .header(ADMIN_HOLD_SESSION_HEADER, adminHoldSessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(bookingService).refreshAdminHold(bookingId, adminHoldSessionId);
    }

    @Test
    void releaseAdminHoldReturns204() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String adminHoldSessionId = "admin-session-1";

        mockMvc.perform(delete("/api/v1/admin/bookings/hold/{id}", bookingId)
                        .header(ADMIN_HOLD_SESSION_HEADER, adminHoldSessionId))
                .andExpect(status().isNoContent());

        verify(bookingService).releaseAdminHold(bookingId, adminHoldSessionId);
    }

    @Test
    void lookupCustomerByPhoneReturns200WhenCustomerExists() throws Exception {
        when(bookingService.findLatestCustomerByPhone("+353831234567"))
                .thenReturn(java.util.Optional.of(AdminBookingCustomerLookupResponseDto.builder()
                        .customerName("Repeat Client")
                        .customerPhone("+353831234567")
                        .customerEmail("repeat@example.com")
                        .build()));

        mockMvc.perform(get("/api/v1/admin/bookings/customer-lookup").param("phone", "+353831234567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Repeat Client"))
                .andExpect(jsonPath("$.customerPhone").value("+353831234567"))
                .andExpect(jsonPath("$.customerEmail").value("repeat@example.com"));
    }

    @Test
    void lookupCustomerByPhoneReturns204WhenCustomerDoesNotExist() throws Exception {
        when(bookingService.findLatestCustomerByPhone("+353000000000"))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/admin/bookings/customer-lookup").param("phone", "+353000000000"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelBookingReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.cancelBookingByAdmin(id)).thenReturn(BookingResponseDto.builder()
                .id(id)
                .status(BookingStatus.CANCELLED)
                .build());

        mockMvc.perform(post("/api/v1/admin/bookings/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(bookingService).cancelBookingByAdmin(id);
    }

    @Test
    void updateBookingReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .customerName("Edited Client")
                .customerPhone("+353831234567")
                .customerEmail("edited@example.com")
                .holdAmount(new java.math.BigDecimal("40.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingService.updateBookingByAdmin(id, request)).thenReturn(BookingResponseDto.builder()
                .id(id)
                .status(BookingStatus.CONFIRMED)
                .customerName("Edited Client")
                .customerEmail("edited@example.com")
                .build());

        mockMvc.perform(put("/api/v1/admin/bookings/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerName").value("Edited Client"))
                .andExpect(jsonPath("$.customerEmail").value("edited@example.com"));

        verify(bookingService).updateBookingByAdmin(id, request);
    }

    @Test
    void getBlacklistEntriesReturns200() throws Exception {
        UUID entryId = UUID.randomUUID();
        when(bookingBlacklistService.getActiveEntries()).thenReturn(List.of(
                BookingBlacklistEntryResponseDto.builder()
                        .id(entryId)
                        .phone("+353831234567")
                        .reason("Chargeback abuse")
                        .build()));

        mockMvc.perform(get("/api/v1/admin/bookings/blacklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(entryId.toString()))
                .andExpect(jsonPath("$[0].phone").value("+353831234567"))
                .andExpect(jsonPath("$[0].reason").value("Chargeback abuse"));
    }

    @Test
    void createBlacklistEntryReturns200() throws Exception {
        BookingBlacklistEntryRequestDto request = BookingBlacklistEntryRequestDto.builder()
                .email("blocked@example.com")
                .phone("+353831234567")
                .reason("Repeated no-shows")
                .build();
        UUID entryId = UUID.randomUUID();

        when(bookingBlacklistService.createEntry(request)).thenReturn(BookingBlacklistEntryResponseDto.builder()
                .id(entryId)
                .email("blocked@example.com")
                .phone("+353831234567")
                .reason("Repeated no-shows")
                .build());

        mockMvc.perform(post("/api/v1/admin/bookings/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId.toString()))
                .andExpect(jsonPath("$.email").value("blocked@example.com"))
                .andExpect(jsonPath("$.phone").value("+353831234567"));
    }

    @Test
    void deleteBlacklistEntryReturns204() throws Exception {
        UUID entryId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/bookings/blacklist/{id}", entryId))
                .andExpect(status().isNoContent());

        verify(bookingBlacklistService).deleteEntry(entryId);
    }
}
