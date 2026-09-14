package com.booking.engine.platform.controller;

import com.booking.engine.entity.AuditAction;
import com.booking.engine.entity.AuditLog;
import com.booking.engine.entity.Availability;
import com.booking.engine.entity.Booking;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.Customer;
import com.booking.engine.entity.Membership;
import com.booking.engine.entity.MembershipRole;
import com.booking.engine.entity.Organization;
import com.booking.engine.entity.Payment;
import com.booking.engine.entity.PaymentStatus;
import com.booking.engine.entity.Review;
import com.booking.engine.entity.Service;
import com.booking.engine.entity.Staff;
import com.booking.engine.entity.User;
import com.booking.engine.entity.UserRole;
import com.booking.engine.platform.repository.AuditLogRepository;
import com.booking.engine.platform.repository.AvailabilityRepository;
import com.booking.engine.platform.repository.BookingRepository;
import com.booking.engine.platform.repository.CustomerRepository;
import com.booking.engine.platform.repository.MembershipRepository;
import com.booking.engine.platform.repository.OrganizationRepository;
import com.booking.engine.platform.repository.PaymentRepository;
import com.booking.engine.platform.repository.ReviewRepository;
import com.booking.engine.platform.repository.ServiceRepository;
import com.booking.engine.platform.repository.StaffRepository;
import com.booking.engine.platform.repository.UserRepository;
import com.booking.engine.platform.security.MembershipGuard;
import com.booking.engine.platform.security.PlatformPrincipal;
import com.booking.engine.platform.service.AuditLogService;
import com.booking.engine.platform.service.AvailabilityCatalog;
import com.booking.engine.platform.service.BookingAnalyticsService;
import com.booking.engine.platform.service.OrganizationServiceCatalog;
import com.booking.engine.platform.service.RedisBookingHoldService;
import com.booking.engine.platform.service.StripePaymentService;
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
import java.util.Map;
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

/** REST API for the multi-tenant booking hierarchy. Per-organization authorization is enforced
 * by {@link MembershipGuard}: every organization-scoped endpoint declares the minimum
 * {@link MembershipRole} a caller's membership must satisfy (OWNER > ADMIN > STAFF). */
@RestController
@RequestMapping("/api/v1")
public class PlatformController {

    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(
            BookingStatus.HELD, BookingStatus.CONFIRMED);

    private final UserRepository users;
    private final CustomerRepository customers;
    private final OrganizationRepository organizations;
    private final ServiceRepository services;
    private final StaffRepository staff;
    private final AvailabilityRepository availability;
    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final MembershipRepository memberships;
    private final OrganizationServiceCatalog serviceCatalog;
    private final AvailabilityCatalog availabilityCatalog;
    private final RedisBookingHoldService holds;
    private final MembershipGuard guard;
    private final StripePaymentService stripePayments;
    private final BookingAnalyticsService analytics;
    private final PaymentRepository payments;
    private final AuditLogRepository auditLogs;
    private final AuditLogService auditLog;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PlatformController(
            UserRepository users,
            CustomerRepository customers,
            OrganizationRepository organizations,
            ServiceRepository services,
            StaffRepository staff,
            AvailabilityRepository availability,
            BookingRepository bookings,
            ReviewRepository reviews,
            MembershipRepository memberships,
            OrganizationServiceCatalog serviceCatalog,
            AvailabilityCatalog availabilityCatalog,
            RedisBookingHoldService holds,
            MembershipGuard guard,
            StripePaymentService stripePayments,
            BookingAnalyticsService analytics,
            PaymentRepository payments,
            AuditLogRepository auditLogs,
            AuditLogService auditLog) {
        this.users = users;
        this.customers = customers;
        this.organizations = organizations;
        this.services = services;
        this.staff = staff;
        this.availability = availability;
        this.bookings = bookings;
        this.reviews = reviews;
        this.memberships = memberships;
        this.serviceCatalog = serviceCatalog;
        this.availabilityCatalog = availabilityCatalog;
        this.holds = holds;
        this.analytics = analytics;
        this.guard = guard;
        this.stripePayments = stripePayments;
        this.payments = payments;
        this.auditLogs = auditLogs;
        this.auditLog = auditLog;
    }

    /** Registers an account able to own organizations. Organization access itself is granted
     * separately via {@link Membership} rows, not by this endpoint. */
    @PostMapping("/providers")
    @Transactional
    public BusinessAccountView createProvider(@Valid @RequestBody AccountRequest request) {
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.PROVIDER);
        users.save(user);
        return new BusinessAccountView(user.getId(), user.getEmail());
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

    /** Organizations the current user is a member of, regardless of role. */
    @GetMapping("/organizations")
    public List<OrganizationView> organizations() {
        return organizations.findAllByMembershipsUserId(guard.currentUserId()).stream().map(this::organizationView).toList();
    }

    @PostMapping("/organizations")
    @Transactional
    public OrganizationView createOrganization(@Valid @RequestBody OrganizationRequest request) {
        UUID userId = guard.currentUserId();
        User currentUser = required(users.findById(userId), "User");
        if (currentUser.getRole() != UserRole.PROVIDER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A provider account is required to create organizations");
        }
        try {
            ZoneId.of(request.timezone());
        } catch (RuntimeException exception) {
            throw badRequest("timezone must be an IANA zone, for example Europe/Zurich");
        }
        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setTimezone(request.timezone());
        organizations.save(organization);

        Membership ownerMembership = new Membership();
        ownerMembership.setUser(currentUser);
        ownerMembership.setOrganization(organization);
        ownerMembership.setRole(MembershipRole.OWNER);
        memberships.save(ownerMembership);

        auditLog.record(organization, userId, AuditAction.ORGANIZATION_CREATED, "ORGANIZATION", organization.getId(),
                "created organization " + organization.getName());
        return organizationView(organization);
    }

    @GetMapping("/organizations/{organizationId}/services")
    public List<ServiceView> services(@PathVariable UUID organizationId) {
        guard.require(organizationId, MembershipRole.STAFF);
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
        Organization organization = guard.require(organizationId, MembershipRole.ADMIN);
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
        guard.require(organizationId, MembershipRole.STAFF);
        return staff.findAllByOrganizationId(organizationId).stream().map(this::staffView).toList();
    }

    @PostMapping("/organizations/{organizationId}/staff")
    public StaffView createStaff(@PathVariable UUID organizationId, @Valid @RequestBody StaffRequest request) {
        Organization organization = guard.require(organizationId, MembershipRole.ADMIN);
        Staff member = new Staff();
        member.setOrganization(organization);
        member.setName(request.name().trim());
        member.setEmail(request.email());
        return staffView(staff.save(member));
    }

    /** The Staff schedule linked to the current user's own membership (set up via
     * {@code POST /organizations/{organizationId}/memberships} with a {@code staffId}). */
    @GetMapping("/organizations/{organizationId}/my-schedule")
    public List<BookingView> mySchedule(@PathVariable UUID organizationId) {
        guard.require(organizationId, MembershipRole.STAFF);
        Staff member = staff.findByUserId(guard.currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No staff schedule is linked to this account"));
        ensureSameOrganization(organizationId, member.getOrganization().getId(), "Staff");
        return bookings.findAllByOrganizationIdOrderByStartTime(organizationId).stream()
                .filter(booking -> booking.getStaff().getId().equals(member.getId()))
                .map(this::bookingView)
                .toList();
    }

    @GetMapping("/organizations/{organizationId}/availability")
    public List<AvailabilityView> availability(@PathVariable UUID organizationId) {
        guard.require(organizationId, MembershipRole.STAFF);
        return availabilityCatalog.availabilityFor(organizationId).stream().map(this::availabilityView).toList();
    }

    @PostMapping("/organizations/{organizationId}/availability")
    public AvailabilityView createAvailability(
            @PathVariable UUID organizationId, @Valid @RequestBody AvailabilityRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw badRequest("endTime must be after startTime");
        }
        Organization organization = guard.require(organizationId, MembershipRole.ADMIN);
        Staff member = required(staff.findById(request.staffId()), "Staff");
        ensureSameOrganization(organizationId, member.getOrganization().getId(), "Staff");

        Availability slot = new Availability();
        slot.setOrganization(organization);
        slot.setStaff(member);
        slot.setDayOfWeek(request.dayOfWeek());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        AvailabilityView view = availabilityView(availability.save(slot));
        availabilityCatalog.invalidate(organizationId);
        return view;
    }

    @GetMapping("/organizations/{organizationId}/bookings")
    public List<BookingView> bookings(@PathVariable UUID organizationId) {
        guard.require(organizationId, MembershipRole.STAFF);
        return bookings.findAllByOrganizationIdOrderByStartTime(organizationId).stream().map(this::bookingView).toList();
    }

    @PostMapping("/organizations/{organizationId}/bookings")
    @Transactional
    public BookingView createBooking(
            @PathVariable UUID organizationId, @Valid @RequestBody BookingRequest request) {
        Organization organization = guard.require(organizationId, MembershipRole.STAFF);
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
        // A Stripe PaymentIntent is created now, but nothing here ever trusts it as "paid" — only
        // the webhook (StripePaymentSyncService) is allowed to move the booking to CONFIRMED.
        StripePaymentService.PaymentIntentSnapshot payment = stripePayments.createPaymentIntentForBooking(
                service.getPrice(), customer.getUser().getEmail(),
                Map.of("organizationId", organizationId.toString(), "staffId", member.getId().toString()));

        Booking booking = new Booking();
        booking.setOrganization(organization);
        booking.setCustomer(customer);
        booking.setStaff(member);
        booking.setService(service);
        booking.setStartTime(request.startTime());
        booking.setEndTime(endTime);
        booking.setStatus(BookingStatus.HELD);
        booking.setAmount(service.getPrice());
        booking.setStripePaymentIntentId(payment.paymentIntentId());
        return bookingView(bookings.save(booking), payment.clientSecret());
    }

    /** The append-only history behind a booking's current status — every applied Stripe event,
     * not just the latest one (see {@code StripePaymentSyncService}). */
    @GetMapping("/organizations/{organizationId}/bookings/{bookingId}/payments")
    public List<PaymentView> payments(@PathVariable UUID organizationId, @PathVariable UUID bookingId) {
        guard.require(organizationId, MembershipRole.ADMIN);
        Booking booking = required(bookings.findById(bookingId), "Booking");
        ensureSameOrganization(organizationId, booking.getOrganization().getId(), "Booking");
        return payments.findAllByBookingIdOrderByCreatedAtDesc(bookingId).stream().map(this::paymentView).toList();
    }

    /** Fed asynchronously by the Kafka analytics consumer when a booking is confirmed — not read
     * from Postgres directly, so a backlog in that consumer never slows this endpoint down. */
    @GetMapping("/organizations/{organizationId}/analytics")
    public AnalyticsView analytics(@PathVariable UUID organizationId) {
        guard.require(organizationId, MembershipRole.OWNER);
        return new AnalyticsView(analytics.confirmedBookingCount(organizationId));
    }

    /** Who changed membership/organization state and when — see {@link AuditLogService}. */
    @GetMapping("/organizations/{organizationId}/audit-logs")
    public List<AuditLogView> auditLogs(@PathVariable UUID organizationId) {
        guard.require(organizationId, MembershipRole.OWNER);
        return auditLogs.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream().map(this::auditLogView).toList();
    }

    @GetMapping("/organizations/{organizationId}/reviews")
    public List<ReviewView> reviews(@PathVariable UUID organizationId) {
        guard.require(organizationId, MembershipRole.STAFF);
        return reviews.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream().map(this::reviewView).toList();
    }

    @PostMapping("/organizations/{organizationId}/reviews")
    public ReviewView createReview(@PathVariable UUID organizationId, @Valid @RequestBody ReviewRequest request) {
        Organization organization = guard.require(organizationId, MembershipRole.STAFF);
        Review review = new Review();
        review.setOrganization(organization);
        review.setCustomer(required(customers.findById(request.customerId()), "Customer"));
        review.setRating(request.rating());
        review.setComment(request.comment());
        return reviewView(reviews.save(review));
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

    private CustomerView customerView(Customer customer) { return new CustomerView(customer.getId(), customer.getDisplayName(), customer.getPhoneNumber()); }
    private OrganizationView organizationView(Organization organization) { return new OrganizationView(organization.getId(), organization.getName(), organization.getTimezone()); }
    private ServiceView serviceView(Service service) { return new ServiceView(service.getId(), service.getName(), service.getDurationMinutes(), service.getPrice()); }
    private ServiceView serviceView(OrganizationServiceCatalog.ServiceSummary service) { return new ServiceView(service.id(), service.name(), service.durationMinutes(), service.price()); }
    private StaffView staffView(Staff member) { return new StaffView(member.getId(), member.getName(), member.getEmail()); }
    private AvailabilityView availabilityView(Availability slot) { return new AvailabilityView(slot.getId(), slot.getStaff().getId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()); }
    private AvailabilityView availabilityView(AvailabilityCatalog.AvailabilitySummary slot) { return new AvailabilityView(slot.id(), slot.staffId(), slot.dayOfWeek(), slot.startTime(), slot.endTime()); }
    private BookingView bookingView(Booking booking) { return bookingView(booking, null); }
    private BookingView bookingView(Booking booking, String clientSecret) { return new BookingView(booking.getId(), booking.getCustomer().getId(), booking.getStaff().getId(), booking.getService().getId(), booking.getStartTime(), booking.getEndTime(), booking.getStatus(), clientSecret); }
    private ReviewView reviewView(Review review) { return new ReviewView(review.getId(), review.getCustomer().getId(), review.getRating(), review.getComment(), review.getCreatedAt()); }
    private PaymentView paymentView(Payment payment) { return new PaymentView(payment.getId(), payment.getStripePaymentIntentId(), payment.getAmount(), payment.getCurrency(), payment.getStatus(), payment.getCreatedAt()); }
    private AuditLogView auditLogView(AuditLog entry) { return new AuditLogView(entry.getId(), entry.getActorUserId(), entry.getAction(), entry.getTargetType(), entry.getTargetId(), entry.getDetail(), entry.getCreatedAt()); }

    public record AccountRequest(@NotBlank @Email String email, @NotBlank String password, @NotBlank String displayName, String phoneNumber) {}
    public record OrganizationRequest(@NotBlank String name, @NotBlank String timezone) {}
    public record ServiceRequest(@NotBlank String name, @NotNull @Positive Integer durationMinutes, @NotNull @DecimalMin("0.00") BigDecimal price) {}
    public record StaffRequest(@NotBlank String name, @Email String email) {}
    public record AvailabilityRequest(@NotNull UUID staffId, @NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
    public record BookingRequest(@NotNull UUID customerId, @NotNull UUID staffId, @NotNull UUID serviceId,
                                 @NotNull Instant startTime, @NotBlank String holdToken) {}
    public record ReviewRequest(@NotNull UUID customerId, @NotNull @Min(1) @Max(5) Integer rating, String comment) {}
    public record BusinessAccountView(UUID userId, String email) {}
    public record CustomerView(UUID id, String displayName, String phoneNumber) {}
    public record OrganizationView(UUID id, String name, String timezone) {}
    public record ServiceView(UUID id, String name, Integer durationMinutes, BigDecimal price) {}
    public record StaffView(UUID id, String name, String email) {}
    public record AvailabilityView(UUID id, UUID staffId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {}
    public record BookingView(UUID id, UUID customerId, UUID staffId, UUID serviceId, Instant startTime, Instant endTime,
                              BookingStatus status, String clientSecret) {}
    public record ReviewView(UUID id, UUID customerId, Integer rating, String comment, Instant createdAt) {}
    public record AnalyticsView(long confirmedBookings) {}
    public record PaymentView(UUID id, String stripePaymentIntentId, BigDecimal amount, String currency,
                              PaymentStatus status, Instant createdAt) {}
    public record AuditLogView(UUID id, UUID actorUserId, AuditAction action, String targetType, UUID targetId,
                               String detail, Instant createdAt) {}
}
