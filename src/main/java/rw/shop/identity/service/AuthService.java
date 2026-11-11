package rw.shop.identity.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.audit.service.AuditService;
import rw.shop.business.domain.Business;
import rw.shop.business.repo.BusinessRepository;
import rw.shop.business.service.TenantGucService;
import rw.shop.common.Ids;
import rw.shop.common.error.ConflictException;
import rw.shop.common.error.UnauthenticatedException;
import rw.shop.common.web.TenantContext;
import rw.shop.config.JwtProperties;
import rw.shop.identity.api.LoginRequest;
import rw.shop.identity.api.LoginResponse;
import rw.shop.identity.api.RegisterBusinessRequest;
import rw.shop.identity.api.RegisterBusinessResponse;
import rw.shop.identity.api.BusinessOption;
import rw.shop.identity.api.TokenResponse;
import rw.shop.identity.domain.Membership;
import rw.shop.identity.domain.MembershipRole;
import rw.shop.identity.domain.MembershipSummary;
import rw.shop.identity.domain.RefreshToken;
import rw.shop.identity.domain.User;
import rw.shop.identity.repo.MembershipRepository;
import rw.shop.identity.repo.RefreshTokenRepository;
import rw.shop.identity.repo.UserRepository;

@Service
public class AuthService {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final LoginRateLimiter rateLimiter;
    private final TenantGucService tenantGucService;
    private final AuditService auditService;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public AuthService(BusinessRepository businessRepository,
            UserRepository userRepository,
            MembershipRepository membershipRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            LoginRateLimiter rateLimiter,
            TenantGucService tenantGucService,
            AuditService auditService,
            Clock clock) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.rateLimiter = rateLimiter;
        this.tenantGucService = tenantGucService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public RegisterBusinessResponse registerBusiness(UUID businessId, RegisterBusinessRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already exists");
        }
        Instant now = clock.instant();

        Business business = new Business();
        business.setId(businessId);
        business.setName(request.businessName().trim());
        business.setBusinessType(trimToNull(request.businessType()));
        business.setPhone(trimToNull(request.phone()));
        business.setCountry(trimToNull(request.country()));
        business.setCurrency("RWF");
        business.setCreatedAt(now);
        business.setUpdatedAt(now);
        businessRepository.save(business);

        User user = new User();
        user.setId(Ids.newId());
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(trimToNull(request.phone()));
        user.setIsActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        Membership membership = new Membership();
        membership.setId(Ids.newId());
        membership.setBusinessId(businessId);
        membership.setUserId(user.getId());
        membership.setRole(MembershipRole.owner);
        membership.setIsActive(true);
        membership.setJoinedAt(now);
        membershipRepository.save(membership);

        TenantContext.set(new TenantContext.Tenant(businessId, user.getId(), membership.getId(), "owner"));
        auditService.record("membership.create", "membership", membership.getId(), null,
                Map.of("userId", user.getId(), "role", "owner", "isActive", true));

        return new RegisterBusinessResponse(businessId, user.getId(), membership.getId());
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        rateLimiter.checkIp(ipAddress);
        String email = normalizeEmail(request.email());
        rateLimiter.checkEmail(email);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null
                || !Boolean.TRUE.equals(user.getIsActive())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimiter.recordFailure(email);
            throw invalidCredentials();
        }

        List<MembershipSummary> active = loadMemberships(user.getId()).stream()
                .filter(MembershipSummary::active)
                .toList();
        if (active.isEmpty()) {
            rateLimiter.recordFailure(email);
            throw invalidCredentials();
        }

        MembershipSummary chosen;
        if (request.businessId() != null) {
            chosen = active.stream()
                    .filter(membership -> membership.businessId().equals(request.businessId()))
                    .findFirst()
                    .orElseThrow(this::invalidCredentials);
        } else if (active.size() == 1) {
            chosen = active.get(0);
        } else {
            return LoginResponse.selection(active.stream()
                    .map(membership -> new BusinessOption(membership.businessId(), membership.businessName()))
                    .toList());
        }

        rateLimiter.reset(email);
        tenantGucService.setBusiness(chosen.businessId());
        Membership membership = membershipRepository
                .findByBusinessIdAndUserId(chosen.businessId(), user.getId())
                .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
                .orElseThrow(this::invalidCredentials);

        JwtService.TokenPair pair = issue(user, membership, UUID.randomUUID());
        return LoginResponse.tokens(pair.accessToken(), pair.refreshToken(), pair.expiresIn());
    }

    @Transactional(readOnly = true)
    public List<BusinessOption> businesses(UUID userId) {
        return loadMemberships(userId).stream()
                .filter(MembershipSummary::active)
                .map(membership -> new BusinessOption(membership.businessId(), membership.businessName()))
                .toList();
    }

    @Transactional
    public TokenResponse switchBusiness(UUID userId, UUID businessId) {
        boolean isMember = loadMemberships(userId).stream()
                .anyMatch(membership -> membership.active() && membership.businessId().equals(businessId));
        if (!isMember) {
            throw invalidCredentials();
        }

        tenantGucService.setBusiness(businessId);
        User user = userRepository.findById(userId).filter(value -> Boolean.TRUE.equals(value.getIsActive()))
                .orElseThrow(this::invalidCredentials);
        Membership membership = membershipRepository.findByBusinessIdAndUserId(businessId, userId)
                .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
                .orElseThrow(this::invalidCredentials);
        JwtService.TokenPair pair = issue(user, membership, UUID.randomUUID());
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresIn());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        var jwt = jwtService.decodeRefreshToken(refreshToken);
        String hash = jwtService.hash(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(this::invalidRefresh);
        Instant now = clock.instant();

        if (stored.getUsedAt() != null) {
            revokeFamily(stored, now);
            throw invalidRefresh();
        }
        if (stored.getRevokedAt() != null || !stored.getExpiresAt().isAfter(now)) {
            throw invalidRefresh();
        }

        UUID userId = uuidClaim(jwt, "userId");
        if (!stored.getUserId().equals(userId)) {
            revokeFamily(stored, now);
            throw invalidRefresh();
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            revokeFamily(stored, now);
            throw invalidRefresh();
        }

        UUID businessId = uuidClaim(jwt, "businessId");
        UUID membershipId = uuidClaim(jwt, "membershipId");
        tenantGucService.setBusiness(businessId);
        Membership membership = membershipRepository.findByIdAndBusinessId(membershipId, businessId)
                .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
                .orElse(null);
        if (membership == null) {
            revokeFamily(stored, now);
            throw invalidRefresh();
        }

        stored.setUsedAt(now);
        refreshTokenRepository.save(stored);
        JwtService.TokenPair pair = issue(user, membership, stored.getFamilyId());
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresIn());
    }

    @Transactional
    public void logout(String refreshToken) {
        try {
            jwtService.decodeRefreshToken(refreshToken);
        } catch (UnauthenticatedException ex) {
            return;
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(jwtService.hash(refreshToken)).orElse(null);
        if (stored == null) {
            return;
        }
        revokeFamily(stored, clock.instant());
    }

    private JwtService.TokenPair issue(User user, Membership membership, UUID familyId) {
        String accessToken = jwtService.issueAccessToken(user.getId(), membership.getBusinessId(),
                membership.getId(), membership.getRole().name());
        String refreshToken = jwtService.issueRefreshToken(user.getId(), membership.getBusinessId(),
                membership.getId(), familyId);

        RefreshToken stored = new RefreshToken();
        stored.setId(Ids.newId());
        stored.setUserId(user.getId());
        stored.setTokenHash(jwtService.hash(refreshToken));
        stored.setFamilyId(familyId);
        stored.setExpiresAt(jwtService.refreshTokenExpiry());
        stored.setCreatedAt(clock.instant());
        refreshTokenRepository.save(stored);

        return new JwtService.TokenPair(accessToken, refreshToken,
                jwtProperties.getAccessTokenMinutes() * 60L);
    }

    private void revokeFamily(RefreshToken token, Instant now) {
        refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
    }

    @SuppressWarnings("unchecked")
    private List<MembershipSummary> loadMemberships(UUID userId) {
        List<Object[]> rows = entityManager
                .createNativeQuery("select business_id, business_name, role, is_active from fn_user_memberships(:userId)")
                .setParameter("userId", userId)
                .getResultList();
        List<MembershipSummary> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new MembershipSummary(
                    (UUID) row[0],
                    (String) row[1],
                    MembershipRole.valueOf(String.valueOf(row[2])),
                    Boolean.TRUE.equals(row[3])));
        }
        return result;
    }

    private UUID uuidClaim(org.springframework.security.oauth2.jwt.Jwt jwt, String name) {
        try {
            return UUID.fromString(jwt.getClaimAsString(name));
        } catch (RuntimeException ex) {
            throw invalidRefresh();
        }
    }

    private UnauthenticatedException invalidCredentials() {
        return new UnauthenticatedException("Invalid email or password");
    }

    private UnauthenticatedException invalidRefresh() {
        return new UnauthenticatedException("Invalid refresh token");
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
