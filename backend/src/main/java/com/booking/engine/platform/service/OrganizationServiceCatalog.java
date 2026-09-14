package com.booking.engine.platform.service;

import com.booking.engine.platform.repository.ServiceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Redis-backed organization service catalog. Cache keys are tenant-scoped. */
@Service
public class OrganizationServiceCatalog {
    private final ServiceRepository services;

    public OrganizationServiceCatalog(ServiceRepository services) { this.services = services; }

    @Cacheable(cacheNames = "organization-services", key = "#organizationId")
    public List<ServiceSummary> servicesFor(UUID organizationId) {
        return services.findAllByOrganizationId(organizationId).stream()
                .map(service -> new ServiceSummary(service.getId(), service.getName(), service.getDurationMinutes(), service.getPrice()))
                .toList();
    }

    @CacheEvict(cacheNames = "organization-services", key = "#organizationId")
    public void invalidate(UUID organizationId) { }

    public record ServiceSummary(UUID id, String name, Integer durationMinutes, BigDecimal price) { }
}
