package com.booking.engine.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.Membership;
import com.booking.engine.entity.MembershipRole;
import com.booking.engine.entity.Organization;
import com.booking.engine.entity.UserRole;
import com.booking.engine.platform.repository.MembershipRepository;
import com.booking.engine.platform.repository.OrganizationRepository;
import com.booking.engine.platform.service.OrganizationCatalog;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class MembershipGuardTest {

    private OrganizationCatalog organizationCatalog;
    private OrganizationRepository organizations;
    private MembershipRepository memberships;
    private MembershipGuard guard;

    private final UUID userId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization();

    @BeforeEach
    void setUp() {
        organizationCatalog = mock(OrganizationCatalog.class);
        organizations = mock(OrganizationRepository.class);
        memberships = mock(MembershipRepository.class);
        guard = new MembershipGuard(organizationCatalog, organizations, memberships);
        organization.setId(organizationId);
        when(organizations.getReferenceById(organizationId)).thenReturn(organization);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new PlatformPrincipal(userId, UserRole.PROVIDER), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerSatisfiesEveryMinimumRole() {
        membershipOf(MembershipRole.OWNER);
        assertThat(guard.require(organizationId, MembershipRole.OWNER)).isSameAs(organization);
        assertThat(guard.require(organizationId, MembershipRole.ADMIN)).isSameAs(organization);
        assertThat(guard.require(organizationId, MembershipRole.STAFF)).isSameAs(organization);
    }

    @Test
    void staffFailsAdminAndOwnerMinimums() {
        membershipOf(MembershipRole.STAFF);
        assertThat(guard.require(organizationId, MembershipRole.STAFF)).isSameAs(organization);
        assertThatThrownBy(() -> guard.require(organizationId, MembershipRole.ADMIN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> guard.require(organizationId, MembershipRole.OWNER))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void adminSatisfiesAdminAndStaffButNotOwner() {
        membershipOf(MembershipRole.ADMIN);
        assertThat(guard.require(organizationId, MembershipRole.ADMIN)).isSameAs(organization);
        assertThat(guard.require(organizationId, MembershipRole.STAFF)).isSameAs(organization);
        assertThatThrownBy(() -> guard.require(organizationId, MembershipRole.OWNER))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void missingMembershipIsForbidden() {
        when(organizationCatalog.summaryOf(organizationId)).thenReturn(Optional.of(summary()));
        when(memberships.findByUserIdAndOrganizationId(userId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.require(organizationId, MembershipRole.STAFF))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void unknownOrganizationIsNotFound() {
        when(organizationCatalog.summaryOf(organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.require(organizationId, MembershipRole.STAFF))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private void membershipOf(MembershipRole role) {
        Membership membership = new Membership();
        membership.setRole(role);
        when(organizationCatalog.summaryOf(organizationId)).thenReturn(Optional.of(summary()));
        when(memberships.findByUserIdAndOrganizationId(userId, organizationId)).thenReturn(Optional.of(membership));
    }

    private OrganizationCatalog.OrganizationSummary summary() {
        return new OrganizationCatalog.OrganizationSummary(organizationId, "Test Org", "Europe/Zurich");
    }
}
