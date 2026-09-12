package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.mapper.EmployeeMapper;
import com.booking.engine.mapper.EmployeeScheduleMapper;
import com.booking.engine.mapper.HairSalonMapper;
import com.booking.engine.mapper.TreatmentMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.EmployeeSchedulePeriodDaySettingsRepository;
import com.booking.engine.repository.EmployeeSchedulePeriodSettingsRepository;
import com.booking.engine.repository.HairSalonRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.AdminBookingHoldService;
import com.booking.engine.service.AdminBookingService;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.DisplayOrderService;
import com.booking.engine.service.EmployeeScheduleService;
import com.booking.engine.service.EmployeeService;
import com.booking.engine.service.HairSalonService;
import com.booking.engine.service.StripePaymentService;
import com.booking.engine.service.TreatmentService;
import com.booking.engine.service.BookingAuditService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingTransactionalOperations;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.EmployeeBookingGuard;
import com.booking.engine.service.EmployeeScheduleDayService;
import com.booking.engine.service.EmployeeSchedulePeriodService;
import com.booking.engine.service.EmployeeScheduleSeedService;
import com.booking.engine.service.EmployeeTreatmentAssignmentService;
import com.booking.engine.service.impl.BookingAdminQueryServiceImpl;
import com.booking.engine.service.impl.BookingHoldAccessTokenService;
import com.booking.engine.service.impl.BookingHoldResponseMapper;
import com.booking.engine.service.impl.BookingPaymentSyncServiceImpl;
import com.booking.engine.service.impl.BookingServiceImpl;
import com.booking.engine.service.impl.BookingStripeMetadataFactory;
import com.booking.engine.service.impl.EmployeeScheduleServiceImpl;
import com.booking.engine.service.impl.EmployeeServiceImpl;
import com.booking.engine.service.impl.HairSalonServiceImpl;
import com.booking.engine.service.impl.TreatmentServiceImpl;
import com.booking.engine.service.payment.StripePaymentIntentVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AdminMethodSecurityTest.MethodSecurityTestConfig.class)
class AdminMethodSecurityTest {

    @Configuration
    @EnableMethodSecurity
    @Import({
            BookingAdminQueryServiceImpl.class,
            BookingHoldAccessTokenService.class,
            BookingHoldResponseMapper.class,
            BookingPaymentSyncServiceImpl.class,
            BookingServiceImpl.class,
            BookingStripeMetadataFactory.class,
            EmployeeServiceImpl.class,
            TreatmentServiceImpl.class,
            HairSalonServiceImpl.class,
            EmployeeScheduleServiceImpl.class
    })
    static class MethodSecurityTestConfig {
    }

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private SlotHoldRepository slotHoldRepository;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private TreatmentRepository treatmentRepository;

    @MockitoBean
    private AvailabilityService availabilityService;

    @MockitoBean
    private BookingBlacklistService bookingBlacklistService;

    @MockitoBean
    private StripePaymentService stripePaymentService;

    @MockitoBean
    private BookingMapper bookingMapper;

    @MockitoBean
    private SecurityAuditLogger securityAuditLogger;

    @MockitoBean
    private BookingValidator bookingValidator;

    @MockitoBean
    private BookingStateMachine bookingStateMachine;

    @MockitoBean
    private BookingTransactionalOperations bookingTransactionalOperations;

    @MockitoBean
    private BookingAuditService bookingAuditService;

    @MockitoBean
    private AdminBookingHoldService adminBookingHoldService;

    @MockitoBean
    private EmployeeDailyScheduleRepository employeeDailyScheduleRepository;

    @MockitoBean
    private HairSalonRepository hairSalonRepository;

    @MockitoBean
    private EmployeeMapper employeeMapper;

    @MockitoBean
    private DisplayOrderService displayOrderService;

    @MockitoBean
    private BookingProperties bookingProperties;

    @MockitoBean
    private PublicBookingHoldRateLimitService publicBookingHoldRateLimitService;

    @MockitoBean
    private HairSalonProperties hairSalonProperties;

    @MockitoBean
    private StripeProperties stripeProperties;

    @MockitoBean
    private StripePaymentIntentVerifier stripePaymentIntentVerifier;

    @MockitoBean
    private EmployeeTreatmentAssignmentService employeeTreatmentAssignmentService;

    @MockitoBean
    private EmployeeBookingGuard employeeBookingGuard;

    @MockitoBean
    private EmployeeScheduleSeedService employeeScheduleSeedService;

    @MockitoBean
    private TreatmentMapper treatmentMapper;

    @MockitoBean
    private HairSalonMapper hairSalonMapper;

    @MockitoBean
    private EmployeeSchedulePeriodSettingsRepository employeeSchedulePeriodSettingsRepository;

    @MockitoBean
    private EmployeeSchedulePeriodDaySettingsRepository employeeSchedulePeriodDaySettingsRepository;

    @MockitoBean
    private EmployeeScheduleMapper employeeScheduleMapper;

    @MockitoBean
    private EmployeeScheduleDayService employeeScheduleDayService;

    @MockitoBean
    private EmployeeSchedulePeriodService employeeSchedulePeriodService;

    @org.springframework.beans.factory.annotation.Autowired
    private AdminBookingService bookingService;

    @org.springframework.beans.factory.annotation.Autowired
    private EmployeeService employeeService;

    @org.springframework.beans.factory.annotation.Autowired
    private TreatmentService treatmentService;

    @org.springframework.beans.factory.annotation.Autowired
    private HairSalonService hairSalonService;

    @org.springframework.beans.factory.annotation.Autowired
    private EmployeeScheduleService employeeScheduleService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAdminBookingsRequiresAuthentication() {
        assertThatThrownBy(() -> bookingService.getAdminBookings(null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(bookingRepository);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAdminBookingsAllowsAdmin() {
        when(bookingProperties.getTimezone()).thenReturn("Europe/Dublin");
        when(bookingRepository.findAllActiveWithEmployeeAndTreatment()).thenReturn(List.of());

        AdminBookingListResponseDto response = bookingService.getAdminBookings(null);

        assertThat(response.getBookings()).isEmpty();
        assertThat(response.getConfirmedCount()).isZero();
        assertThat(response.getFilteredCount()).isZero();
    }

    @Test
    @WithMockUser(roles = "USER")
    void createEmployeeRejectsNonAdminUsers() {
        assertThatThrownBy(() -> employeeService.createEmployee(null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(displayOrderService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTreatmentAllowsAdmin() {
        TreatmentRequestDto request = TreatmentRequestDto.builder()
                .name("Clipper Cut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .description("Classic clipper service")
                .displayOrder(0)
                .build();

        TreatmentEntity entity = TreatmentEntity.builder()
                .name("Clipper Cut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .description("Classic clipper service")
                .displayOrder(0)
                .build();

        TreatmentResponseDto responseDto = TreatmentResponseDto.builder()
                .id(UUID.randomUUID())
                .name("Clipper Cut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .description("Classic clipper service")
                .displayOrder(0)
                .active(true)
                .build();

        when(displayOrderService.resolveDisplayOrder(eq(0), same(treatmentRepository))).thenReturn(0);
        when(treatmentMapper.toEntity(request)).thenReturn(entity);
        when(treatmentRepository.save(entity)).thenReturn(entity);
        when(treatmentMapper.toDto(entity)).thenReturn(responseDto);

        TreatmentResponseDto created = treatmentService.createTreatment(request);

        assertThat(created.getName()).isEqualTo("Clipper Cut");
        verify(treatmentRepository).save(entity);
    }

    @Test
    void updateHairSalonDataRequiresAuthentication() {
        HairSalonRequestDto request = HairSalonRequestDto.builder()
                .name("Salon")
                .address("Main Street 1")
                .build();

        assertThatThrownBy(() -> hairSalonService.updateHairSalonData(request))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(hairSalonRepository);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateHairSalonDataAllowsAdmin() {
        UUID salonId = UUID.randomUUID();
        HairSalonEntity salon = HairSalonEntity.builder()
                .name("Old Salon")
                .address("Old address")
                .build();

        HairSalonRequestDto request = HairSalonRequestDto.builder()
                .name("Updated Salon")
                .address("Main Street 1")
                .email("salon@example.com")
                .phone("+353123456")
                .description("Updated description")
                .build();

        when(hairSalonProperties.getId()).thenReturn(salonId);
        when(hairSalonRepository.findById(salonId)).thenReturn(Optional.of(salon));

        hairSalonService.updateHairSalonData(request);

        verify(hairSalonMapper).updateFromDto(request, salon);
    }

    @Test
    void getPeriodSettingsRequiresAuthentication() {
        assertThatThrownBy(() -> employeeScheduleService.getPeriodSettings())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(employeeSchedulePeriodSettingsRepository);
    }
}
