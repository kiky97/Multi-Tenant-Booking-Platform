package com.booking.engine.platform.service;

import com.booking.engine.platform.repository.OrganizationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Redis-backed existence check for organizations. Every organization-scoped request goes
 * through {@code MembershipGuard}, which calls this on every single call — caching it avoids a
 * database round trip on what is otherwise the hottest query in the app. */
@Service
public class OrganizationCatalog {
    private final OrganizationRepository organizations;

    public OrganizationCatalog(OrganizationRepository organizations) { this.organizations = organizations; }

    @Cacheable(cacheNames = "organizations", key = "#organizationId")
    public Optional<OrganizationSummary> summaryOf(UUID organizationId) {
        return organizations.findById(organizationId)
                .map(organization -> new OrganizationSummary(organization.getId(), organization.getName(), organization.getTimezone()));
    }

    public record OrganizationSummary(UUID id, String name, String timezone) { }
}
