package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.service.BookingAuditService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingAdminQueryServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingMapper mapper;

    @Mock
    private BookingAuditService bookingAuditService;

    private BookingAdminQueryServiceImpl bookingAdminQueryService;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");

        bookingAdminQueryService = new BookingAdminQueryServiceImpl(
                bookingRepository,
                mapper,
                bookingAuditService,
                bookingProperties);

        org.mockito.Mockito.lenient().when(mapper.toDto(any(BookingEntity.class)))
                .thenAnswer(invocation -> toDto(invocation.getArgument(0)));
    }

    @Test
    void getAdminBookingsShouldReturnMappedBookingsWithCounts() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));
        BookingEntity confirmedBooking = buildBooking(
                "Confirmed Client",
                "+353831111111",
                "Jacob",
                "Haircut",
                BookingStatus.CONFIRMED,
                today.plusDays(1),
                LocalTime.of(10, 0),
                LocalDateTime.of(2026, 1, 1, 9, 0));
        BookingEntity pendingBooking = buildBooking(
                "Pending Client",
                "+353832222222",
                "Mia",
                "Beard Trim",
                BookingStatus.PENDING,
                today.plusDays(2),
                LocalTime.of(11, 0),
                LocalDateTime.of(2026, 1, 2, 9, 0));

        when(bookingRepository.findAllActiveWithEmployeeAndTreatment())
                .thenReturn(List.of(pendingBooking, confirmedBooking));

        AdminBookingListResponseDto response = bookingAdminQueryService.getAdminBookings(null);

        assertEquals(2, response.getBookings().size());
        assertEquals(1, response.getConfirmedCount());
        assertEquals(2, response.getFilteredCount());
        assertEquals(List.of(confirmedBooking.getId(), pendingBooking.getId()), bookingIds(response));
        assertEquals("Confirmed Client", response.getBookings().get(0).getCustomerName());
        assertEquals("Haircut", response.getBookings().get(0).getTreatmentName());
    }

    @Test
    void getAdminBookingsShouldPreserveCurrentSortOrder() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));
        BookingEntity futureLater = buildBooking(
                "Future Later",
                "+353831111111",
                "Jacob",
                "Haircut",
                BookingStatus.CONFIRMED,
                today.plusDays(2),
                LocalTime.of(9, 0),
                LocalDateTime.of(2026, 1, 2, 9, 0));
        BookingEntity futureEarly = buildBooking(
                "Future Early",
                "+353832222222",
                "Mia",
                "Beard Trim",
                BookingStatus.CONFIRMED,
                today.plusDays(1),
                LocalTime.of(15, 0),
                LocalDateTime.of(2026, 1, 3, 9, 0));
        BookingEntity pastRecent = buildBooking(
                "Past Recent",
                "+353833333333",
                "Noah",
                "Colour",
                BookingStatus.DONE,
                today.minusDays(1),
                LocalTime.of(16, 0),
                LocalDateTime.of(2026, 1, 4, 9, 0));
        BookingEntity pastOld = buildBooking(
                "Past Old",
                "+353834444444",
                "Ava",
                "Wash",
                BookingStatus.CANCELLED,
                today.minusDays(3),
                LocalTime.of(9, 0),
                LocalDateTime.of(2026, 1, 5, 9, 0));

        when(bookingRepository.findAllActiveWithEmployeeAndTreatment())
                .thenReturn(List.of(pastOld, futureLater, pastRecent, futureEarly));

        AdminBookingListResponseDto response = bookingAdminQueryService.getAdminBookings(null);

        assertEquals(
                List.of(futureEarly.getId(), futureLater.getId(), pastRecent.getId(), pastOld.getId()),
                bookingIds(response));
    }

    @Test
    void getAdminBookingsShouldFilterByCustomerNameWithNormalizedCaseInsensitiveSearch() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));
        BookingEntity aliceBooking = buildBooking(
                "Alice Murphy",
                "+353831111111",
                "Jacob",
                "Haircut",
                BookingStatus.CONFIRMED,
                today.plusDays(1),
                LocalTime.of(10, 0),
                LocalDateTime.of(2026, 1, 1, 9, 0));
        BookingEntity bobBooking = buildBooking(
                "Bob Kelly",
                "+353832222222",
                "Mia",
                "Beard Trim",
                BookingStatus.CONFIRMED,
                today.plusDays(2),
                LocalTime.of(11, 0),
                LocalDateTime.of(2026, 1, 2, 9, 0));

        when(bookingRepository.findAllActiveWithEmployeeAndTreatment())
                .thenReturn(List.of(bobBooking, aliceBooking));

        AdminBookingListResponseDto response = bookingAdminQueryService.getAdminBookings("  aLiCe  ");

        assertEquals(1, response.getFilteredCount());
        assertEquals(List.of(aliceBooking.getId()), bookingIds(response));
    }

    @Test
    void getAdminBookingsShouldFilterByPhoneDigits() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));
        BookingEntity matchingBooking = buildBooking(
                "Phone Client",
                "+353 83 123 4567",
                "Jacob",
                "Haircut",
                BookingStatus.CONFIRMED,
                today.plusDays(1),
                LocalTime.of(10, 0),
                LocalDateTime.of(2026, 1, 1, 9, 0));
        BookingEntity otherBooking = buildBooking(
                "Other Client",
                "+353 85 000 0000",
                "Mia",
                "Beard Trim",
                BookingStatus.CONFIRMED,
                today.plusDays(2),
                LocalTime.of(11, 0),
                LocalDateTime.of(2026, 1, 2, 9, 0));

        when(bookingRepository.findAllActiveWithEmployeeAndTreatment())
                .thenReturn(List.of(otherBooking, matchingBooking));

        AdminBookingListResponseDto response = bookingAdminQueryService.getAdminBookings("353831234567");

        assertEquals(1, response.getFilteredCount());
        assertEquals(List.of(matchingBooking.getId()), bookingIds(response));
    }

    @Test
    void getAdminBookingsShouldTreatEmptyOrBlankSearchAsUnfiltered() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));
        BookingEntity firstBooking = buildBooking(
                "First Client",
                "+353831111111",
                "Jacob",
                "Haircut",
                BookingStatus.CONFIRMED,
                today.plusDays(1),
                LocalTime.of(10, 0),
                LocalDateTime.of(2026, 1, 1, 9, 0));
        BookingEntity secondBooking = buildBooking(
                "Second Client",
                "+353832222222",
                "Mia",
                "Beard Trim",
                BookingStatus.PENDING,
                today.plusDays(2),
                LocalTime.of(11, 0),
                LocalDateTime.of(2026, 1, 2, 9, 0));

        when(bookingRepository.findAllActiveWithEmployeeAndTreatment())
                .thenReturn(List.of(secondBooking, firstBooking));

        assertEquals(
                List.of(firstBooking.getId(), secondBooking.getId()),
                bookingIds(bookingAdminQueryService.getAdminBookings("")));
        assertEquals(
                List.of(firstBooking.getId(), secondBooking.getId()),
                bookingIds(bookingAdminQueryService.getAdminBookings("   ")));
    }

    @Test
    void getAdminBookingsShouldNotMatchTreatmentEmployeeStatusOrDateSearch() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));
        BookingEntity booking = buildBooking(
                "Customer Only",
                "+353831111111",
                "Jacob",
                "Haircut",
                BookingStatus.DONE,
                today.plusDays(1),
                LocalTime.of(10, 0),
                LocalDateTime.of(2026, 1, 1, 9, 0));

        when(bookingRepository.findAllActiveWithEmployeeAndTreatment())
                .thenReturn(List.of(booking));

        assertEquals(0, bookingAdminQueryService.getAdminBookings("Jacob").getFilteredCount());
        assertEquals(0, bookingAdminQueryService.getAdminBookings("Haircut").getFilteredCount());
        assertEquals(0, bookingAdminQueryService.getAdminBookings("DONE").getFilteredCount());
        assertEquals(0, bookingAdminQueryService.getAdminBookings(today.plusDays(1).toString()).getFilteredCount());
    }

    private List<UUID> bookingIds(AdminBookingListResponseDto response) {
        return response.getBookings().stream()
                .map(BookingResponseDto::getId)
                .toList();
    }

    private BookingResponseDto toDto(BookingEntity booking) {
        return BookingResponseDto.builder()
                .id(booking.getId())
                .employeeId(booking.getEmployee().getId())
                .employeeName(booking.getEmployee().getName())
                .treatmentId(booking.getTreatment().getId())
                .treatmentName(booking.getTreatment().getName())
                .bookingDate(booking.getBookingDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .customerName(booking.getCustomerName())
                .customerEmail(booking.getCustomerEmail())
                .customerPhone(booking.getCustomerPhone())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .build();
    }

    private BookingEntity buildBooking(
            String customerName,
            String customerPhone,
            String employeeName,
            String treatmentName,
            BookingStatus status,
            LocalDate bookingDate,
            LocalTime startTime,
            LocalDateTime createdAt) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(UUID.randomUUID());
        employee.setName(employeeName);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(UUID.randomUUID());
        treatment.setName(treatmentName);

        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setActive(true);
        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setCustomerName(customerName);
        booking.setCustomerEmail(customerName.toLowerCase().replace(" ", ".") + "@example.com");
        booking.setCustomerPhone(customerPhone);
        booking.setBookingDate(bookingDate);
        booking.setStartTime(startTime);
        booking.setEndTime(startTime.plusHours(1));
        booking.setStatus(status);
        booking.setCreatedAt(createdAt);
        return booking;
    }
}
