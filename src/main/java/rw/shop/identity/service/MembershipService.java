package rw.shop.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.audit.service.AuditService;
import rw.shop.common.Ids;
import rw.shop.common.error.ConflictException;
import rw.shop.common.error.NotFoundException;
import rw.shop.common.pagination.PageRequests;
import rw.shop.common.pagination.PageResponse;
import rw.shop.common.web.TenantContext;
import rw.shop.identity.api.CreateMembershipRequest;
import rw.shop.identity.api.MembershipResponse;
import rw.shop.identity.api.UpdateMembershipRequest;
import rw.shop.identity.domain.Membership;
import rw.shop.identity.domain.MembershipRole;
import rw.shop.identity.domain.User;
import rw.shop.identity.repo.MembershipRepository;
import rw.shop.identity.repo.UserRepository;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public MembershipService(MembershipRepository membershipRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, AuditService auditService, Clock clock) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional(readOnly = true)
    public PageResponse<MembershipResponse> list(int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        Page<Membership> memberships = membershipRepository.findByBusinessId(businessId,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "joinedAt")));
        List<UUID> userIds = memberships.getContent().stream().map(Membership::getUserId).toList();
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MembershipResponse> items = memberships.getContent().stream()
                .map(membership -> toResponse(membership, users.get(membership.getUserId())))
                .toList();
        return new PageResponse<>(items, memberships.getNumber(), memberships.getSize(),
                memberships.getTotalElements());
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public MembershipResponse create(CreateMembershipRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already exists");
        }
        Instant now = clock.instant();

        User user = new User();
        user.setId(Ids.newId());
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(trimToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setIsActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        Membership membership = new Membership();
        membership.setId(Ids.newId());
        membership.setBusinessId(businessId);
        membership.setUserId(user.getId());
        membership.setRole(request.role());
        membership.setIsActive(true);
        membership.setJoinedAt(now);
        membershipRepository.save(membership);

        auditService.record("membership.create", "membership", membership.getId(), null,
                Map.of("userId", user.getId(), "role", membership.getRole().name(), "isActive", true));

        return toResponse(membership, user);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public MembershipResponse update(UUID membershipId, UpdateMembershipRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        Membership membership = membershipRepository.findByIdAndBusinessId(membershipId, businessId)
                .orElseThrow(() -> new NotFoundException("Membership not found"));

        boolean wasActiveOwner = membership.getRole() == MembershipRole.owner
                && Boolean.TRUE.equals(membership.getIsActive());
        boolean staysActiveOwner = request.role() == MembershipRole.owner
                && Boolean.TRUE.equals(request.isActive());
        if (wasActiveOwner && !staysActiveOwner) {
            long owners = membershipRepository.countByBusinessIdAndRoleAndIsActiveTrue(businessId,
                    MembershipRole.owner);
            if (owners <= 1) {
                throw new ConflictException("The last active owner cannot be demoted or deactivated");
            }
        }

        Map<String, Object> before = Map.of(
                "role", membership.getRole().name(),
                "isActive", Boolean.TRUE.equals(membership.getIsActive()));
        membership.setRole(request.role());
        membership.setIsActive(request.isActive());
        membershipRepository.save(membership);
        Map<String, Object> after = Map.of(
                "role", membership.getRole().name(),
                "isActive", Boolean.TRUE.equals(membership.getIsActive()));
        auditService.record("membership.update", "membership", membership.getId(), before, after);

        User user = userRepository.findById(membership.getUserId()).orElse(null);
        return toResponse(membership, user);
    }

    private MembershipResponse toResponse(Membership membership, User user) {
        return new MembershipResponse(
                membership.getId(),
                membership.getUserId(),
                user == null ? null : user.getFullName(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getPhone(),
                membership.getRole(),
                Boolean.TRUE.equals(membership.getIsActive()),
                membership.getJoinedAt());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
