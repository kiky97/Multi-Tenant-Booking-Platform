package com.booking.engine.platform.controller;

import com.booking.engine.entity.Availability;
import com.booking.engine.entity.Booking;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.Customer;
import com.booking.engine.entity.Organization;
import com.booking.engine.entity.Provider;
import com.booking.engine.entity.Review;
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
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.context.SecurityContextHolder;

/** REST API for the multi-tenant booking hierarchy. */
@RestController
@RequestMapping("/api/v1")
public class PlatformController {

    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(
            BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final UserRepository users;
    private final ProviderRepository providers;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final ServiceRepository services;
    private final StaffRepository staff;
    private final AvailabilityRepository availability;
    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final OrganizationServiceCatalog serviceCatalog;
    private final RedisBookingHoldService holds;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PlatformController(
            UserRepository users,
            ProviderRepository providers,
            CustomerRepository customers,
            OrganizationRepository organizations,
            ServiceRepository services,
            StaffRepository staff,
            AvailabilityRepository availability,
            BookingRepository bookings,
            ReviewRepository reviews,
            OrganizationServiceCatalog serviceCatalog,
            RedisBookingHoldService holds) {
        this.users = users;
        this.providers = providers;
        this.customers = customers;
        this.organizations = organizations;
        this.services = services;
        this.staff = staff;
        this.availability = availability;
        this.bookings = bookings;
        this.reviews = reviews;
        this.serviceCatalog = serviceCatalog;
        this.holds = holds;
    }

    @PostMapping("/providers")
    @Transactional
    public ProviderView createProvider(@Valid @RequestBody AccountRequest request) {
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.PROVIDER);
        users.save(user);

        Provider provider = new Provider();
        provider.setUser(user);
        provider.setBusinessName(request.displayName().trim());
        return providerView(providers.save(provider));
    }

    @PostMapping("/customers")
    @Transactional
    public CustomerView createCustomer(@Valid @RequestBody AccountRequest request) {
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.CUSTOMER);
        users.save(user);

        Customer customer = new Customer();
        customer.setUser(user);
        customer.setDisplayName(request.displayName().trim());
        customer.setPhoneNumber(request.phoneNumber());
        return customerView(customers.save(customer));
    }

    @GetMapping("/organizations")
    public List<OrganizationView> organizations() {
        Provider provider = currentProvider();
        return organizations.findAllByProviderId(provider.getId()).stream().map(this::organizationView).toList();
    }

    @PostMapping("/providers/{providerId}/organizations")
    public OrganizationView createOrganization(
            @PathVariable UUID providerId, @Valid @RequestBody OrganizationRequest request) {
        Provider provider = currentProvider();
        if (!provider.getId().equals(providerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Providers can create organizations only for themselves");
        }
        try {
            ZoneId.of(request.timezone());
        } catch (RuntimeException exception) {
            throw badRequest("timezone must be an IANA zone, for example Europe/Zurich");
        }
        Organization organization = new Organization();
        organization.setProvider(provider);
        organization.setName(request.name().trim());
        organization.setTimezone(request.timezone());
        return organizationView(organizations.save(organization));
    }

    @GetMapping("/organizations/{organizationId}/services")
    public List<ServiceView> services(@PathVariable UUID organizationId) {
        requireOrganization(organizationId);
        return serviceCatalog.servicesFor(organizationId).stream().map(this::serviceView).toList();
    }

    /** Tenant-scoped alternative to the nested endpoint. */
    @GetMapping("/services")
    public List<ServiceView> currentOrganizationServices(@RequestHeader("X-Organization-Id") UUID organizationId) {
        return services(organizationId);
    }

    @PostMapping("/organizations/{organizationId}/services")
    public ServiceView createService(
            @PathVariable UUID organizationId, @Valid @RequestBody ServiceRequest request) {
        Organization organization = requireOrganization(organizationId);
        Service service = new Service();
        service.setOrganization(organization);
        service.setName(request.name().trim());
        service.setDurationMinutes(request.durationMinutes());
        service.setPrice(request.price());
        ServiceView view = serviceView(services.save(service));
        serviceCatalog.invalidate(organizationId);
        return view;
    }

    @GetMapping("/organizations/{organizationId}/staff")
    public List<StaffView> staff(@PathVariable UUID organizationId) {
        requireOrganization(organizationId);
        return staff.findAllByOrganizationId(organizationId).stream().map(this::staffView).toList();
    }

    @PostMapping("/organizations/{organizationId}/staff")
    public StaffView createStaff(@PathVariable UUID organizationId, @Valid @RequestBody StaffRequest request) {
        Staff member = new Staff();
        member.setOrganization(requireOrganization(organizationId));
        member.setName(request.name().trim());
        member.setEmail(request.email());
        return staffView(staff.save(member));
    }

    @GetMapping("/organizations/{organizationId}/availability")
    public List<AvailabilityView> availability(@PathVariable UUID organizationId) {
        requireOrganization(organizationId);
        return availability.findAllByOrganizationId(organizationId).stream().map(this::availabilityView).toList();
    }

    @PostMapping("/organizations/{organizationId}/availability")
    public AvailabilityView createAvailability(
            @PathVariable UUID organizationId, @Valid @RequestBody AvailabilityRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw badRequest("endTime must be after startTime");
        }
        Organization organization = requireOrganization(organizationId);
        Staff member = required(staff.findById(request.staffId()), "Staff");
        ensureSameOrganization(organizationId, member.getOrganization().getId(), "Staff");

        Availability slot = new Availability();
        slot.setOrganization(organization);
        slot.setStaff(member);
        slot.setDayOfWeek(request.dayOfWeek());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        return availabilityView(availability.save(slot));
    }

    @GetMapping("/organizations/{organizationId}/bookings")
    public List<BookingView> bookings(@PathVariable UUID organizationId) {
        requireOrganization(organizationId);
        return bookings.findAllByOrganizationIdOrderByStartTime(organizationId).stream().map(this::bookingView).toList();
    }

    @PostMapping("/organizations/{organizationId}/bookings")
    @Transactional
    public BookingView createBooking(
            @PathVariable UUID organizationId, @Valid @RequestBody BookingRequest request) {
        Organization organization = requireOrganization(organizationId);
        Customer customer = required(customers.findById(request.customerId()), "Customer");
        Staff member = required(staff.findById(request.staffId()), "Staff");
        Service service = required(services.findById(request.serviceId()), "Service");
        ensureSameOrganization(organizationId, member.getOrganization().getId(), "Staff");
        ensureSameOrganization(organizationId, service.getOrganization().getId(), "Service");

        Instant endTime = request.startTime().plusSeconds(service.getDurationMinutes() * 60L);

        // The Redis hold is the primary defense against double-booking: it was acquired atomically
        // when the customer selected this slot, so a second concurrent request for the same
        // organization/staff/time window fails here with 409 before either reaches the database.
        if (!holds.consume(request.holdToken(), organizationId, member.getId(), request.startTime(), endTime)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This time slot hold is invalid or has expired; please select the slot again");
        }

        // Defense in depth: catches bookings created without going through the hold flow (e.g. an
        // earlier confirmed booking) or a hold that briefly outlived its own TTL.
        if (bookings.existsByStaffIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                member.getId(), BLOCKING_STATUSES, endTime, request.startTime())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Staff member is already booked for this time");
        }
        Booking booking = new Booking();
        booking.setOrganization(organization);
        booking.setCustomer(customer);
        booking.setStaff(member);
        booking.setService(service);
        booking.setStartTime(request.startTime());
        booking.setEndTime(endTime);
        booking.setStatus(BookingStatus.PENDING);
        return bookingView(bookings.save(booking));
    }

    @GetMapping("/organizations/{organizationId}/reviews")
    public List<ReviewView> reviews(@PathVariable UUID organizationId) {
        requireOrganization(organizationId);
        return reviews.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream().map(this::reviewView).toList();
    }

    @PostMapping("/organizations/{organizationId}/reviews")
    public ReviewView createReview(@PathVariable UUID organizationId, @Valid @RequestBody ReviewRequest request) {
        Review review = new Review();
        review.setOrganization(requireOrganization(organizationId));
        review.setCustomer(required(customers.findById(request.customerId()), "Customer"));
        review.setRating(request.rating());
        review.setComment(request.comment());
        return reviewView(reviews.save(review));
    }

    private Organization requireOrganization(UUID id) {
        Organization organization = required(organizations.findById(id), "Organization");
        Provider provider = currentProvider();
        if (!organization.getProvider().getId().equals(provider.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization does not belong to the current provider");
        }
        return organization;
    }

    private Provider currentProvider() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof PlatformPrincipal platformPrincipal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return providers.findByUserId(platformPrincipal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "A provider account is required"));
    }

    private void ensureSameOrganization(UUID expected, UUID actual, String resource) {
        if (!expected.equals(actual)) {
            throw badRequest(resource + " does not belong to this organization");
        }
    }

    private static <T> T required(java.util.Optional<T> entity, String label) {
        return entity.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found"));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ProviderView providerView(Provider provider) { return new ProviderView(provider.getId(), provider.getBusinessName()); }
    private CustomerView customerView(Customer customer) { return new CustomerView(customer.getId(), customer.getDisplayName(), customer.getPhoneNumber()); }
    private OrganizationView organizationView(Organization organization) { return new OrganizationView(organization.getId(), organization.getProvider().getId(), organization.getName(), organization.getTimezone()); }
    private ServiceView serviceView(Service service) { return new ServiceView(service.getId(), service.getName(), service.getDurationMinutes(), service.getPrice()); }
    private ServiceView serviceView(OrganizationServiceCatalog.ServiceSummary service) { return new ServiceView(service.id(), service.name(), service.durationMinutes(), service.price()); }
    private StaffView staffView(Staff member) { return new StaffView(member.getId(), member.getName(), member.getEmail()); }
    private AvailabilityView availabilityView(Availability slot) { return new AvailabilityView(slot.getId(), slot.getStaff().getId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()); }
    private BookingView bookingView(Booking booking) { return new BookingView(booking.getId(), booking.getCustomer().getId(), booking.getStaff().getId(), booking.getService().getId(), booking.getStartTime(), booking.getEndTime(), booking.getStatus()); }
    private ReviewView reviewView(Review review) { return new ReviewView(review.getId(), review.getCustomer().getId(), review.getRating(), review.getComment(), review.getCreatedAt()); }

    public record AccountRequest(@NotBlank @Email String email, @NotBlank String password, @NotBlank String displayName, String phoneNumber) {}
    public record OrganizationRequest(@NotBlank String name, @NotBlank String timezone) {}
    public record ServiceRequest(@NotBlank String name, @NotNull @Positive Integer durationMinutes, @NotNull @DecimalMin("0.00") BigDecimal price) {}
    public record StaffRequest(@NotBlank String name, @Email String email) {}
    public record AvailabilityRequest(@NotNull UUID staffId, @NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
    public record BookingRequest(@NotNull UUID customerId, @NotNull UUID staffId, @NotNull UUID serviceId,
                                 @NotNull Instant startTime, @NotBlank String holdToken) {}
    public record ReviewRequest(@NotNull UUID customerId, @NotNull @Min(1) @Max(5) Integer rating, String comment) {}
    public record ProviderView(UUID id, String businessName) {}
    public record CustomerView(UUID id, String displayName, String phoneNumber) {}
    public record OrganizationView(UUID id, UUID providerId, String name, String timezone) {}
    public record ServiceView(UUID id, String name, Integer durationMinutes, BigDecimal price) {}
    public record StaffView(UUID id, String name, String email) {}
    public record AvailabilityView(UUID id, UUID staffId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {}
    public record BookingView(UUID id, UUID customerId, UUID staffId, UUID serviceId, Instant startTime, Instant endTime, BookingStatus status) {}
    public record ReviewView(UUID id, UUID customerId, Integer rating, String comment, Instant createdAt) {}
}
