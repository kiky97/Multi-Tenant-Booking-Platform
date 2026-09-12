package com.booking.engine.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.booking.engine.dto.EmployeeRequestDto;
import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.dto.PublicBookingSummaryResponseDto;
import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.entity.HairSalonHoursEntity;
import com.booking.engine.entity.TreatmentEntity;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MapperImplCoverageTest {

    // Instantiate generated mappers directly so coverage stays independent of Spring wiring.
    private final EmployeeMapper employeeMapper = new EmployeeMapperImpl();
    private final TreatmentMapper treatmentMapper = new TreatmentMapperImpl();
    private final BookingMapper bookingMapper = new BookingMapperImpl();
    private final HairSalonHoursMapper hairSalonHoursMapper = new HairSalonHoursMapperImpl();
    private final EmployeeScheduleMapper employeeScheduleMapper = new EmployeeScheduleMapperImpl();

    private HairSalonMapper hairSalonMapper;

    @BeforeEach
    void setUp() {
        hairSalonMapper = new HairSalonMapperImpl();
        ReflectionTestUtils.setField(hairSalonMapper, "hairSalonHoursMapper", hairSalonHoursMapper);
    }

    @Test
    void employeeMapperCoversNullEntityUpdateAndDtoMappings() {
        assertThat(employeeMapper.toEntity(null)).isNull();
        assertThat(employeeMapper.toDto(null)).isNull();

        EmployeeRequestDto request = EmployeeRequestDto.builder()
                .name("Alex")
                .role("Master Employee")
                .bio("Senior stylist")
                .photoUrl("https://cdn.example.com/alex.jpg")
                .displayOrder(3)
                .treatmentIds(List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .build();

        EmployeeEntity entity = employeeMapper.toEntity(request);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getActive()).isTrue();
        assertThat(entity.getName()).isEqualTo("Alex");
        assertThat(entity.getRole()).isEqualTo("Master Employee");
        assertThat(entity.getBio()).isEqualTo("Senior stylist");
        assertThat(entity.getPhotoUrl()).isEqualTo("https://cdn.example.com/alex.jpg");
        assertThat(entity.getDisplayOrder()).isEqualTo(3);
        assertThat(entity.getBookable()).isFalse();

        EmployeeEntity existing = EmployeeEntity.builder()
                .name("Old Name")
                .role("Old Role")
                .bio("Old Bio")
                .photoUrl("https://cdn.example.com/old.jpg")
                .displayOrder(1)
                .active(false)
                .build();

        employeeMapper.updateFromDto(EmployeeRequestDto.builder().name("Updated").build(), existing);
        employeeMapper.updateFromDto(null, existing);

        assertThat(existing.getName()).isEqualTo("Updated");
        assertThat(existing.getRole()).isEqualTo("Old Role");
        assertThat(existing.getActive()).isTrue();

        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 10, 9, 30);
        LocalDateTime updatedAt = createdAt.plusHours(2);
        entity.setId(id);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        entity.setBookable(true);
        entity.setProvidedTreatments(Set.of(
                TreatmentEntity.builder()
                        .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .name("Cut")
                        .displayOrder(2)
                        .build(),
                TreatmentEntity.builder()
                        .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                        .name("Color")
                        .displayOrder(1)
                        .build()));

        EmployeeResponseDto response = employeeMapper.toDto(entity);

        assertThat(response).isEqualTo(EmployeeResponseDto.builder()
                .id(id)
                .name("Alex")
                .role("Master Employee")
                .bio("Senior stylist")
                .photoUrl("https://cdn.example.com/alex.jpg")
                .displayOrder(3)
                .bookable(true)
                .treatmentIds(List.of(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .active(true)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build());
    }

    @Test
    void treatmentMapperCoversNullEntityUpdateAndDtoMappings() {
        assertThat(treatmentMapper.toEntity(null)).isNull();
        assertThat(treatmentMapper.toDto(null)).isNull();

        TreatmentRequestDto request = TreatmentRequestDto.builder()
                .name("Premium Cut")
                .durationMinutes(45)
                .price(new BigDecimal("35.00"))
                .photoUrl("https://cdn.example.com/cut.jpg")
                .description("Scissors and fade")
                .displayOrder(2)
                .build();

        TreatmentEntity entity = treatmentMapper.toEntity(request);

        assertThat(entity.getActive()).isTrue();
        assertThat(entity.getName()).isEqualTo("Premium Cut");
        assertThat(entity.getDurationMinutes()).isEqualTo(45);
        assertThat(entity.getPrice()).isEqualByComparingTo("35.00");
        assertThat(entity.getPhotoUrl()).isEqualTo("https://cdn.example.com/cut.jpg");
        assertThat(entity.getDescription()).isEqualTo("Scissors and fade");
        assertThat(entity.getDisplayOrder()).isEqualTo(2);

        TreatmentEntity existing = TreatmentEntity.builder()
                .name("Legacy")
                .durationMinutes(30)
                .price(new BigDecimal("20.00"))
                .photoUrl("legacy")
                .description("legacy")
                .displayOrder(9)
                .active(false)
                .build();

        treatmentMapper.updateFromDto(TreatmentRequestDto.builder()
                .durationMinutes(50)
                .price(new BigDecimal("40.00"))
                .build(), existing);
        treatmentMapper.updateFromDto(null, existing);

        assertThat(existing.getName()).isEqualTo("Legacy");
        assertThat(existing.getDurationMinutes()).isEqualTo(50);
        assertThat(existing.getPrice()).isEqualByComparingTo("40.00");
        assertThat(existing.getActive()).isTrue();

        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 11, 10, 0);
        LocalDateTime updatedAt = createdAt.plusMinutes(15);
        entity.setId(id);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);

        TreatmentResponseDto response = treatmentMapper.toDto(entity);

        assertThat(response).isEqualTo(TreatmentResponseDto.builder()
                .id(id)
                .name("Premium Cut")
                .durationMinutes(45)
                .price(new BigDecimal("35.00"))
                .photoUrl("https://cdn.example.com/cut.jpg")
                .description("Scissors and fade")
                .displayOrder(2)
                .active(true)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build());
    }

    @Test
    void bookingMapperFlattensCustomerDataAndHandlesNullRelations() {
        assertThat(bookingMapper.toEntity(null)).isNull();
        assertThat(bookingMapper.toDto(null)).isNull();

        BookingRequestDto request = BookingRequestDto.builder()
                .employeeId(UUID.randomUUID())
                .treatmentId(UUID.randomUUID())
                .bookingDate(LocalDate.of(2030, 1, 15))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .paymentMethodId("pm_card_visa")
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .build();

        BookingEntity entity = bookingMapper.toEntity(request);

        assertThat(entity.getCustomerName()).isEqualTo("John Doe");
        assertThat(entity.getCustomerEmail()).isEqualTo("john@example.com");
        assertThat(entity.getCustomerPhone()).isEqualTo("+353870000000");
        assertThat(entity.getBookingDate()).isEqualTo(LocalDate.of(2030, 1, 15));
        assertThat(entity.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(entity.getEndTime()).isEqualTo(LocalTime.of(11, 0));

        BookingEntity existing = BookingEntity.builder()
                .customerName("Existing Name")
                .customerEmail("existing@example.com")
                .customerPhone("111")
                .bookingDate(LocalDate.of(2031, 2, 1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();

        bookingMapper.updateFromDto(BookingRequestDto.builder()
                .bookingDate(LocalDate.of(2031, 3, 1))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(14, 45))
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("Updated Name")
                        .email("updated@example.com")
                        .build())
                .build(), existing);
        bookingMapper.updateFromDto(null, existing);

        assertThat(existing.getCustomerName()).isEqualTo("Updated Name");
        assertThat(existing.getCustomerEmail()).isEqualTo("updated@example.com");
        assertThat(existing.getCustomerPhone()).isEqualTo("111");
        assertThat(existing.getBookingDate()).isEqualTo(LocalDate.of(2031, 3, 1));
        assertThat(existing.getStartTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(existing.getEndTime()).isEqualTo(LocalTime.of(14, 45));

        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        entity.setId(bookingId);
        entity.setEmployee(EmployeeEntity.builder().id(employeeId).build());
        entity.setTreatment(TreatmentEntity.builder().id(treatmentId).build());
        entity.setStatus(BookingStatus.CONFIRMED);
        entity.setExpiresAt(LocalDateTime.of(2030, 1, 15, 11, 15));
        entity.setStripePaymentIntentId("pi_123");
        entity.setStripePaymentStatus("succeeded");
        entity.setHoldAmount(new BigDecimal("10.00"));
        entity.setPaymentCapturedAt(LocalDateTime.of(2030, 1, 16, 9, 0));
        entity.setPaymentReleasedAt(LocalDateTime.of(2030, 1, 16, 9, 5));
        entity.setCreatedAt(LocalDateTime.of(2030, 1, 10, 8, 0));
        entity.setUpdatedAt(LocalDateTime.of(2030, 1, 10, 9, 0));

        BookingResponseDto response = bookingMapper.toDto(entity);

        assertThat(response.getId()).isEqualTo(bookingId);
        assertThat(response.getEmployeeId()).isEqualTo(employeeId);
        assertThat(response.getTreatmentId()).isEqualTo(treatmentId);
        assertThat(response.getCustomerName()).isEqualTo("John Doe");
        assertThat(response.getCustomerEmail()).isEqualTo("john@example.com");
        assertThat(response.getCustomerPhone()).isEqualTo("+353870000000");
        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.getStripePaymentIntentId()).isEqualTo("pi_123");
        assertThat(response.getHoldAmount()).isEqualByComparingTo("10.00");

        PublicBookingSummaryResponseDto publicSummary = bookingMapper.toPublicSummaryDto(entity);

        assertThat(publicSummary).isEqualTo(PublicBookingSummaryResponseDto.builder()
                .id(bookingId)
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.of(2030, 1, 15))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .status(BookingStatus.CONFIRMED)
                .build());

        BookingResponseDto withoutRelations = bookingMapper.toDto(BookingEntity.builder()
                .id(UUID.randomUUID())
                .customerName("No Relations")
                .build());

        assertThat(withoutRelations.getEmployeeId()).isNull();
        assertThat(withoutRelations.getTreatmentId()).isNull();
    }

    @Test
    void hairSalonMappersCoverCollectionAndNullHandling() {
        assertThat(hairSalonHoursMapper.toDto(null)).isNull();
        assertThat(hairSalonMapper.toEntity(null)).isNull();
        assertThat(hairSalonMapper.toDto(null)).isNull();

        HairSalonHoursEntity hoursEntity = HairSalonHoursEntity.builder()
                .dayOfWeek(DayOfWeek.FRIDAY)
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .build();

        assertThat(hairSalonHoursMapper.toDto(hoursEntity).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);

        HairSalonRequestDto request = HairSalonRequestDto.builder()
                .name("Royal Chair")
                .description("Luxury employee studio")
                .email("contact@royal.example")
                .phone("+353123456")
                .address("1 King Street")
                .build();

        HairSalonEntity entity = hairSalonMapper.toEntity(request);
        assertThat(entity.getName()).isEqualTo("Royal Chair");
        assertThat(entity.getAddress()).isEqualTo("1 King Street");

        HairSalonEntity existing = HairSalonEntity.builder()
                .name("Old Name")
                .description("Old Description")
                .email("old@example.com")
                .phone("123")
                .address("Old Address")
                .build();

        hairSalonMapper.updateFromDto(HairSalonRequestDto.builder()
                .description("Updated Description")
                .address("Updated Address")
                .build(), existing);
        hairSalonMapper.updateFromDto(null, existing);

        assertThat(existing.getName()).isEqualTo("Old Name");
        assertThat(existing.getDescription()).isEqualTo("Updated Description");
        assertThat(existing.getAddress()).isEqualTo("Updated Address");

        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 2, 1, 9, 0);
        LocalDateTime updatedAt = createdAt.plusDays(1);
        entity.setId(id);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        entity.setWorkingHours(List.of(hoursEntity));

        HairSalonResponseDto response = hairSalonMapper.toDto(entity);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo("Royal Chair");
        assertThat(response.getWorkingHours()).hasSize(1);
        assertThat(response.getWorkingHours().get(0).getOpenTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void employeeScheduleMapperCoversRequestResponseAndNullHandling() {
        assertThat(employeeScheduleMapper.toEntity(null)).isNull();
        assertThat(employeeScheduleMapper.toDto(null)).isNull();

        EmployeeScheduleRequestDto request = EmployeeScheduleRequestDto.builder()
                .workingDate(LocalDate.of(2030, 6, 5))
                .workingDay(true)
                .openTime(LocalTime.of(10, 0))
                .closeTime(LocalTime.of(18, 0))
                .breakStartTime(LocalTime.of(13, 0))
                .breakEndTime(LocalTime.of(13, 30))
                .build();

        EmployeeDailyScheduleEntity entity = employeeScheduleMapper.toEntity(request);

        assertThat(entity.getEmployee()).isNull();
        assertThat(entity.getWorkingDate()).isEqualTo(LocalDate.of(2030, 6, 5));
        assertThat(entity.isWorkingDay()).isTrue();
        assertThat(entity.getBreakEndTime()).isEqualTo(LocalTime.of(13, 30));

        UUID id = UUID.randomUUID();
        entity.setId(id);

        EmployeeScheduleResponseDto response = employeeScheduleMapper.toDto(entity);

        assertThat(response).isEqualTo(EmployeeScheduleResponseDto.builder()
                .id(id)
                .workingDate(LocalDate.of(2030, 6, 5))
                .workingDay(true)
                .openTime(LocalTime.of(10, 0))
                .closeTime(LocalTime.of(18, 0))
                .breakStartTime(LocalTime.of(13, 0))
                .breakEndTime(LocalTime.of(13, 30))
                .build());
    }
}
