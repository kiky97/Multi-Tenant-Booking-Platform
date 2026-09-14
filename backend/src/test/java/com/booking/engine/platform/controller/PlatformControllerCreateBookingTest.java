package com.booking.engine.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.Customer;
import com.booking.engine.entity.Organization;
import com.booking.engine.entity.Provider;
import com.booking.engine.entity.Service;
import com.booking.engine.entity.Staff;
import com.booking.engine.entity.User;
import com.booking.engine.entity.UserRole;
import com.booking.engine.platform.repository.AvailabilityRepository;
import com.booking.engine.platform.repository.BookingRepository;
import com.booking.engine.platform.repository.CustomerRepository;
import com.booking.engine.platform.repository.OrganizationRepository;
import com.booking.engine.platform.repository.ProviderRepository;
import com.booking.engine.platform.repository.ReviewRepository;
import com.booking.engine.platform.repository.ServiceRepository;
import com.booking.engine.platform.repository.StaffRepository;
import com.booking.engine.platform.repository.UserRepository;
import com.booking.engine.platform.security.PlatformPrincipal;
import com.booking.engine.platform.service.OrganizationServiceCatalog;
import com.booking.engine.platform.service.RedisBookingHoldService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifies that confirming a booking requires burning a valid Redis slot hold first: this is the
 * mechanism that actually prevents two concurrent customers from double-booking the same staff
 * member's time slot, so a stale/forged/missing hold token must never reach the database.
 */
class PlatformControllerCreateBookingTest {

    private OrganizationRepository organizations;
    private ProviderRepository providers;
    private CustomerRepository customers;
    private StaffRepository staff;
    private ServiceRepository services;
    private BookingRepository bookings;
    private RedisBookingHoldService holds;
    private PlatformController controller;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final Instant startTime = Instant.parse("2026-01-01T10:00:00Z");
    private final Instant endTime = startTime.plusSeconds(1800);

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        providers = mock(ProviderRepository.class);
        customers = mock(CustomerRepository.class);
        organizations = mock(OrganizationRepository.class);
        services = mock(ServiceRepository.class);
        staff = mock(StaffRepository.class);
        AvailabilityRepository availability = mock(AvailabilityRepository.class);
        bookings = mock(BookingRepository.class);
        ReviewRepository reviews = mock(ReviewRepository.class);
        OrganizationServiceCatalog serviceCatalog = mock(OrganizationServiceCatalog.class);
        holds = mock(RedisBookingHoldService.class);

        controller = new PlatformController(users, providers, customers, organizations, services, staff,
                availability, bookings, reviews, serviceCatalog, holds);

        Provider provider = new Provider();
        provider.setId(UUID.randomUUID());
        User user = new User();
        user.setId(UUID.randomUUID());
        provider.setUser(user);

        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setProvider(provider);

        Customer customer = new Customer();
        customer.setId(customerId);

        Staff staffMember = new Staff();
        staffMember.setId(staffId);
        staffMember.setOrganization(organization);

        Service service = new Service();
        service.setId(serviceId);
        service.setOrganization(organization);
        service.setDurationMinutes(30);
        service.setPrice(BigDecimal.TEN);

        when(providers.findByUserId(any(UUID.class))).thenReturn(Optional.of(provider));
        when(organizations.findById(organizationId)).thenReturn(Optional.of(organization));
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));
        when(staff.findById(staffId)).thenReturn(Optional.of(staffMember));
        when(services.findById(serviceId)).thenReturn(Optional.of(service));
        when(bookings.existsByStaffIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(staffId), any(), eq(endTime), eq(startTime))).thenReturn(false);
        when(bookings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new PlatformPrincipal(user.getId(), UserRole.PROVIDER), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bookingIsRejectedWhenTheHoldIsMissingOrExpired() {
        when(holds.consume("expired-token", organizationId, staffId, startTime, endTime)).thenReturn(false);
        PlatformController.BookingRequest request =
                new PlatformController.BookingRequest(customerId, staffId, serviceId, startTime, "expired-token");

        assertThatThrownBy(() -> controller.createBooking(organizationId, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value())
                        .isEqualTo(409));

        verify(bookings, never()).save(any());
    }

    @Test
    void bookingSucceedsAndConsumesTheHoldWhenItIsValid() {
        when(holds.consume("valid-token", organizationId, staffId, startTime, endTime)).thenReturn(true);
        PlatformController.BookingRequest request =
                new PlatformController.BookingRequest(customerId, staffId, serviceId, startTime, "valid-token");

        PlatformController.BookingView view = controller.createBooking(organizationId, request);

        assertThat(view.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(view.staffId()).isEqualTo(staffId);
        verify(holds).consume("valid-token", organizationId, staffId, startTime, endTime);
    }
}
