package com.booking.engine.platform.controller;

import com.booking.engine.entity.Service;
import com.booking.engine.entity.Staff;
import com.booking.engine.platform.repository.OrganizationRepository;
import com.booking.engine.platform.repository.ServiceRepository;
import com.booking.engine.platform.repository.StaffRepository;
import com.booking.engine.platform.service.RedisBookingHoldService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/booking-holds")
public class BookingHoldController {
    private final OrganizationRepository organizations;
    private final StaffRepository staff;
    private final ServiceRepository services;
    private final RedisBookingHoldService holds;

    public BookingHoldController(OrganizationRepository organizations, StaffRepository staff,
            ServiceRepository services, RedisBookingHoldService holds) {
        this.organizations = organizations;
        this.staff = staff;
        this.services = services;
        this.holds = holds;
    }

    @PostMapping
    public HoldResponse create(@Valid @RequestBody HoldRequest request) {
        if (organizations.findById(request.organizationId()).isEmpty()) throw notFound("Organization");
        Staff member = staff.findById(request.staffId()).orElseThrow(() -> notFound("Staff"));
        Service service = services.findById(request.serviceId()).orElseThrow(() -> notFound("Service"));
        if (!member.getOrganization().getId().equals(request.organizationId())
                || !service.getOrganization().getId().equals(request.organizationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff and service must belong to the requested organization");
        }
        Instant endTime = request.startTime().plusSeconds(service.getDurationMinutes() * 60L);
        RedisBookingHoldService.Hold hold = holds.create(request.organizationId(), request.staffId(), request.startTime(), endTime);
        return new HoldResponse(hold.holdToken(), request.startTime(), endTime, hold.expiresAt());
    }

    @DeleteMapping("/{holdToken}")
    public void release(@PathVariable String holdToken) { holds.release(holdToken); }

    private static ResponseStatusException notFound(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " not found");
    }

    public record HoldRequest(@NotNull UUID organizationId, @NotNull UUID staffId, @NotNull UUID serviceId,
                              @NotNull Instant startTime) { }
    public record HoldResponse(String holdToken, Instant startTime, Instant endTime, Instant expiresAt) { }
}
