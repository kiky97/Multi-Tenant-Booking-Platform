package com.booking.engine.platform.controller;

import com.booking.engine.entity.Membership;
import com.booking.engine.entity.MembershipRole;
import com.booking.engine.entity.Organization;
import com.booking.engine.entity.Staff;
import com.booking.engine.entity.User;
import com.booking.engine.platform.repository.MembershipRepository;
import com.booking.engine.platform.repository.StaffRepository;
import com.booking.engine.platform.repository.UserRepository;
import com.booking.engine.platform.security.MembershipGuard;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Manages who belongs to an organization and at what {@link MembershipRole}. Inviting and
 * removing members is OWNER-only; anyone in the organization can see the team list. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/memberships")
public class MembershipController {
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final StaffRepository staff;
    private final MembershipGuard guard;

    public MembershipController(MembershipRepository memberships, UserRepository users, StaffRepository staff,
            MembershipGuard guard) {
        this.memberships = memberships;
        this.users = users;
        this.staff = staff;
        this.guard = guard;
    }

    @GetMapping
    public List<MembershipView> list(@PathVariable UUID organizationId) {
        guard.require(organizationId, MembershipRole.STAFF);
        return memberships.findAllByOrganizationId(organizationId).stream().map(this::membershipView).toList();
    }

    @PostMapping
    @Transactional
    public MembershipView invite(@PathVariable UUID organizationId, @Valid @RequestBody InviteRequest request) {
        Organization organization = guard.require(organizationId, MembershipRole.OWNER);
        User invitee = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No account found for that email; ask them to register first"));
        if (memberships.existsByUserIdAndOrganizationId(invitee.getId(), organizationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This user is already a member of the organization");
        }

        Membership membership = new Membership();
        membership.setUser(invitee);
        membership.setOrganization(organization);
        membership.setRole(request.role());
        memberships.save(membership);

        if (request.staffId() != null) {
            Staff member = staff.findById(request.staffId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found"));
            if (!member.getOrganization().getId().equals(organizationId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff does not belong to this organization");
            }
            if (member.getUser() != null && !member.getUser().getId().equals(invitee.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Staff is already linked to a different account");
            }
            member.setUser(invitee);
            staff.save(member);
        }

        return membershipView(membership);
    }

    @DeleteMapping("/{membershipId}")
    @Transactional
    public void remove(@PathVariable UUID organizationId, @PathVariable UUID membershipId) {
        guard.require(organizationId, MembershipRole.OWNER);
        Membership membership = memberships.findByIdAndOrganizationId(membershipId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        if (membership.getRole() == MembershipRole.OWNER
                && memberships.countByOrganizationIdAndRole(organizationId, MembershipRole.OWNER) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An organization must keep at least one owner");
        }
        memberships.delete(membership);
    }

    private MembershipView membershipView(Membership membership) {
        return new MembershipView(membership.getId(), membership.getUser().getId(), membership.getUser().getEmail(),
                membership.getRole());
    }

    public record InviteRequest(@NotBlank @Email String email, @NotNull MembershipRole role, UUID staffId) {}
    public record MembershipView(UUID id, UUID userId, String email, MembershipRole role) {}
}
