package com.booking.engine.platform.security;

import com.booking.engine.entity.Membership;
import com.booking.engine.entity.MembershipRole;
import com.booking.engine.entity.Organization;
import com.booking.engine.platform.repository.MembershipRepository;
import com.booking.engine.platform.repository.OrganizationRepository;
import com.booking.engine.platform.service.OrganizationCatalog;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the caller's per-organization {@link Membership} and enforces a minimum
 * {@link MembershipRole}. This is the single source of truth for "can the current user do X in
 * organization Y" — replaces the old provider-ownership check now that an organization can have
 * more than one member with different privilege levels.
 */
@Component
public class MembershipGuard {
    private final OrganizationCatalog organizationCatalog;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;

    public MembershipGuard(OrganizationCatalog organizationCatalog, OrganizationRepository organizations,
            MembershipRepository memberships) {
        this.organizationCatalog = organizationCatalog;
        this.organizations = organizations;
        this.memberships = memberships;
    }

    public Organization require(UUID organizationId, MembershipRole minimumRole) {
        // Existence check reads through the cache (this runs on every organization-scoped
        // request); the entity reference below is a zero-query JPA proxy, not a second lookup.
        organizationCatalog.summaryOf(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        Membership membership = memberships.findByUserIdAndOrganizationId(currentUserId(), organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this organization"));
        if (!membership.getRole().satisfies(minimumRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, minimumRole + " role or higher is required");
        }
        return organizations.getReferenceById(organizationId);
    }

    public UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof PlatformPrincipal platformPrincipal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return platformPrincipal.userId();
    }
}
