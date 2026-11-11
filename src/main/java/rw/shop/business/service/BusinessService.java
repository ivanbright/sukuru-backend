package rw.shop.business.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.audit.service.AuditService;
import rw.shop.common.Ids;
import rw.shop.business.api.BusinessRequest;
import rw.shop.business.api.BusinessResponse;
import rw.shop.business.domain.Business;
import rw.shop.business.repo.BusinessRepository;
import rw.shop.common.error.NotFoundException;
import rw.shop.common.web.TenantContext;
import rw.shop.billing.service.AccountPlanService;
import rw.shop.identity.domain.Membership;
import rw.shop.identity.domain.MembershipRole;
import rw.shop.identity.repo.MembershipRepository;

@Service
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final AuditService auditService;
    private final Clock clock;
    private final MembershipRepository membershipRepository;
    private final TenantGucService tenantGucService;
    private final AccountPlanService accountPlanService;

    public BusinessService(BusinessRepository businessRepository, AuditService auditService, Clock clock,
            MembershipRepository membershipRepository, TenantGucService tenantGucService,
            AccountPlanService accountPlanService) {
        this.businessRepository = businessRepository;
        this.auditService = auditService;
        this.clock = clock;
        this.membershipRepository = membershipRepository;
        this.tenantGucService = tenantGucService;
        this.accountPlanService = accountPlanService;
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public BusinessResponse createForCurrentOwner(BusinessRequest request) {
        UUID userId = TenantContext.requireUserId();
        accountPlanService.requireWorkspaceAllowance(userId);
        UUID businessId = Ids.newId();
        Instant now = clock.instant();
        tenantGucService.setBusiness(businessId);

        Business business = new Business();
        business.setId(businessId);
        business.setName(request.name().trim());
        business.setBusinessType(trimToNull(request.businessType()));
        business.setPhone(trimToNull(request.phone()));
        business.setEmail(trimToNull(request.email()));
        business.setAddress(trimToNull(request.address()));
        business.setCountry(trimToNull(request.country()));
        business.setCurrency(trimToNull(request.currency()) == null ? "RWF" : request.currency().trim().toUpperCase(Locale.ROOT));
        business.setCreatedAt(now);
        business.setUpdatedAt(now);
        businessRepository.save(business);

        Membership membership = new Membership();
        membership.setId(Ids.newId());
        membership.setBusinessId(businessId);
        membership.setUserId(userId);
        membership.setRole(MembershipRole.owner);
        membership.setIsActive(true);
        membership.setJoinedAt(now);
        membershipRepository.save(membership);

        TenantContext.set(new TenantContext.Tenant(businessId, userId, membership.getId(), "owner"));
        BusinessResponse created = toResponse(business);
        auditService.record("business.create", "business", businessId, null, created);
        return created;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public BusinessResponse get() {
        UUID businessId = TenantContext.requireBusinessId();
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));
        return toResponse(business);
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public BusinessResponse update(BusinessRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));

        BusinessResponse before = toResponse(business);

        business.setName(request.name().trim());
        business.setBusinessType(request.businessType());
        business.setPhone(request.phone());
        business.setEmail(request.email());
        business.setAddress(request.address());
        business.setCountry(request.country());
        business.setCurrency(request.currency());
        business.setUpdatedAt(clock.instant());

        businessRepository.save(business);

        BusinessResponse after = toResponse(business);
        auditService.record("business.update", "business", businessId, before, after);

        return after;
    }

    private BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(),
                business.getName(),
                business.getBusinessType(),
                business.getPhone(),
                business.getEmail(),
                business.getAddress(),
                business.getCountry(),
                business.getCurrency(),
                business.getCreatedAt(),
                business.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
