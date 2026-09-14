package com.booking.engine.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.AuditAction;
import com.booking.engine.entity.Membership;
import com.booking.engine.entity.MembershipRole;
import com.booking.engine.entity.Organization;
import com.booking.engine.entity.Staff;
import com.booking.engine.entity.User;
import com.booking.engine.entity.UserRole;
import com.booking.engine.platform.repository.MembershipRepository;
import com.booking.engine.platform.repository.OrganizationRepository;
import com.booking.engine.platform.repository.StaffRepository;
import com.booking.engine.platform.repository.UserRepository;
import com.booking.engine.platform.security.MembershipGuard;
import com.booking.engine.platform.security.PlatformPrincipal;
import com.booking.engine.platform.service.AuditLogService;
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

class MembershipControllerTest {

    private OrganizationRepository organizations;
    private MembershipRepository memberships;
    private UserRepository users;
    private StaffRepository staff;
    private AuditLogService auditLogService;
    private MembershipController controller;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final Organization organization = new Organization();

    @BeforeEach
    void setUp() {
        OrganizationCatalog organizationCatalog = mock(OrganizationCatalog.class);
        organizations = mock(OrganizationRepository.class);
        memberships = mock(MembershipRepository.class);
        users = mock(UserRepository.class);
        staff = mock(StaffRepository.class);
        auditLogService = mock(AuditLogService.class);
        MembershipGuard guard = new MembershipGuard(organizationCatalog, organizations, memberships);
        controller = new MembershipController(memberships, users, staff, guard, auditLogService);

        organization.setId(organizationId);
        Membership ownerMembership = new Membership();
        ownerMembership.setRole(MembershipRole.OWNER);
        when(organizationCatalog.summaryOf(organizationId))
                .thenReturn(Optional.of(new OrganizationCatalog.OrganizationSummary(organizationId, "Org", "Europe/Zurich")));
        when(organizations.getReferenceById(organizationId)).thenReturn(organization);
        when(memberships.findByUserIdAndOrganizationId(ownerId, organizationId)).thenReturn(Optional.of(ownerMembership));
        when(memberships.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new PlatformPrincipal(ownerId, UserRole.PROVIDER), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invitesAnExistingUserByEmail() {
        User invitee = new User();
        invitee.setId(UUID.randomUUID());
        invitee.setEmail("staff@example.com");
        when(users.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(invitee));
        when(memberships.existsByUserIdAndOrganizationId(invitee.getId(), organizationId)).thenReturn(false);

        MembershipController.MembershipView view = controller.invite(organizationId,
                new MembershipController.InviteRequest("staff@example.com", MembershipRole.STAFF, null));

        assertThat(view.email()).isEqualTo("staff@example.com");
        assertThat(view.role()).isEqualTo(MembershipRole.STAFF);
        verify(auditLogService).record(eq(organization), eq(ownerId), eq(AuditAction.MEMBERSHIP_INVITED),
                eq("MEMBERSHIP"), any(), any());
    }

    @Test
    void invitingAnUnregisteredEmailIsNotFound() {
        when(users.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.invite(organizationId,
                new MembershipController.InviteRequest("ghost@example.com", MembershipRole.STAFF, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invitingAnExistingMemberConflicts() {
        User invitee = new User();
        invitee.setId(UUID.randomUUID());
        invitee.setEmail("already@example.com");
        when(users.findByEmailIgnoreCase("already@example.com")).thenReturn(Optional.of(invitee));
        when(memberships.existsByUserIdAndOrganizationId(invitee.getId(), organizationId)).thenReturn(true);

        assertThatThrownBy(() -> controller.invite(organizationId,
                new MembershipController.InviteRequest("already@example.com", MembershipRole.STAFF, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invitingWithAStaffIdLinksTheStaffRowToTheInvitee() {
        User invitee = new User();
        invitee.setId(UUID.randomUUID());
        invitee.setEmail("stylist@example.com");
        when(users.findByEmailIgnoreCase("stylist@example.com")).thenReturn(Optional.of(invitee));
        when(memberships.existsByUserIdAndOrganizationId(invitee.getId(), organizationId)).thenReturn(false);

        UUID staffId = UUID.randomUUID();
        Staff staffRow = new Staff();
        staffRow.setId(staffId);
        staffRow.setOrganization(organization);
        when(staff.findById(staffId)).thenReturn(Optional.of(staffRow));
        when(staff.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        controller.invite(organizationId, new MembershipController.InviteRequest("stylist@example.com", MembershipRole.STAFF, staffId));

        assertThat(staffRow.getUser()).isEqualTo(invitee);
    }

    @Test
    void removingTheLastOwnerIsRejected() {
        UUID membershipId = UUID.randomUUID();
        Membership targetMembership = new Membership();
        targetMembership.setId(membershipId);
        targetMembership.setRole(MembershipRole.OWNER);
        when(memberships.findByIdAndOrganizationId(membershipId, organizationId)).thenReturn(Optional.of(targetMembership));
        when(memberships.countByOrganizationIdAndRole(organizationId, MembershipRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> controller.remove(organizationId, membershipId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(memberships, never()).delete(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void removingAMemberWhenAnotherOwnerRemainsSucceeds() {
        UUID membershipId = UUID.randomUUID();
        User removedUser = new User();
        removedUser.setEmail("owner2@example.com");
        Membership targetMembership = new Membership();
        targetMembership.setId(membershipId);
        targetMembership.setRole(MembershipRole.OWNER);
        targetMembership.setUser(removedUser);
        when(memberships.findByIdAndOrganizationId(membershipId, organizationId)).thenReturn(Optional.of(targetMembership));
        when(memberships.countByOrganizationIdAndRole(organizationId, MembershipRole.OWNER)).thenReturn(2L);

        controller.remove(organizationId, membershipId);

        verify(memberships).delete(targetMembership);
        verify(auditLogService).record(eq(organization), eq(ownerId), eq(AuditAction.MEMBERSHIP_REMOVED),
                eq("MEMBERSHIP"), eq(membershipId), any());
    }
}
