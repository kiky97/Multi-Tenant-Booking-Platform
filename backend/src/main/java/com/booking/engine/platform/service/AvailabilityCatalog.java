package com.booking.engine.platform.service;

import com.booking.engine.platform.repository.AvailabilityRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Redis-backed organization availability listing. Cache keys are tenant-scoped. */
@Service
public class AvailabilityCatalog {
    private final AvailabilityRepository availability;

    public AvailabilityCatalog(AvailabilityRepository availability) { this.availability = availability; }

    @Cacheable(cacheNames = "organization-availability", key = "#organizationId")
    public List<AvailabilitySummary> availabilityFor(UUID organizationId) {
        return availability.findAllByOrganizationId(organizationId).stream()
                .map(slot -> new AvailabilitySummary(slot.getId(), slot.getStaff().getId(), slot.getDayOfWeek(),
                        slot.getStartTime(), slot.getEndTime()))
                .toList();
    }

    @CacheEvict(cacheNames = "organization-availability", key = "#organizationId")
    public void invalidate(UUID organizationId) { }

    public record AvailabilitySummary(UUID id, UUID staffId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) { }
}
