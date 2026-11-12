package rw.shop.billing.service;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import rw.shop.billing.api.BillingOverviewResponse;
import rw.shop.billing.api.UpgradeRequest;
import rw.shop.billing.api.UpgradeRequestResponse;
import rw.shop.common.Ids;
import rw.shop.common.error.ConflictException;
import rw.shop.common.web.TenantContext;

@Service
public class BillingService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final String adminKey;
    private final ApplicationEventPublisher eventPublisher;

    public BillingService(JdbcTemplate jdbcTemplate, Clock clock,
            @Value("${app.billing.admin-key:}") String adminKey,
            ApplicationEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.adminKey = adminKey;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public BillingOverviewResponse overview(String mobileMoneyNumber, String supportEmail) {
        UUID userId = TenantContext.requireUserId();
        setUser(userId);
        List<Map<String, Object>> plans = jdbcTemplate.queryForList(
                "select plan_code, status from account_plans where user_id = ?", userId);
        String plan = plans.isEmpty() ? "free" : String.valueOf(plans.get(0).get("plan_code"));
        String status = plans.isEmpty() ? "active" : String.valueOf(plans.get(0).get("status"));
        Integer workspaceCount = jdbcTemplate.queryForObject("""
                select count(*) from fn_user_memberships(?)
                where role = 'owner' and is_active = true
                """, Integer.class, userId);
        List<UpgradeRequestResponse> requests = recentRequests(userId, 1);
        return new BillingOverviewResponse(plan, status, workspaceCount == null ? 0 : workspaceCount,
                "premium".equals(plan) && "active".equals(status) ? 5 : 1,
                mobileMoneyNumber, supportEmail, requests.isEmpty() ? null : requests.get(0));
    }

    @Transactional
    public UpgradeRequestResponse requestUpgrade(UpgradeRequest request) {
        UUID userId = TenantContext.requireUserId();
        setUser(userId);
        List<Map<String, Object>> currentPlan = jdbcTemplate.queryForList(
                "select plan_code from account_plans where user_id = ? and status = 'active'", userId);
        if (!currentPlan.isEmpty() && "premium".equals(currentPlan.get(0).get("plan_code"))) {
            throw new ConflictException("Your account is already on the Premium plan");
        }
        List<UpgradeRequestResponse> pending = recentRequests(userId, 20).stream()
                .filter(item -> "pending".equals(item.status()))
                .toList();
        if (!pending.isEmpty()) {
            throw new ConflictException("You already have a pending upgrade request. Contact support if you need help.");
        }

        UUID id = Ids.newId();
        Instant now = clock.instant();
        try {
            jdbcTemplate.update("""
                    insert into upgrade_requests (id, user_id, plan_code, payment_method, payment_reference, status, requested_at)
                    values (?, ?, 'premium', ?, ?, 'pending', ?)
                    """, id, userId, request.paymentMethod(), trimToNull(request.paymentReference()), Timestamp.from(now));
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("You already have a pending upgrade request. Contact support if you need help.");
        }
        Map<String, Object> user = jdbcTemplate.queryForMap(
                "select full_name, email from users where id = ?", userId);
        eventPublisher.publishEvent(new UpgradeRequestSubmitted(
                id, userId, String.valueOf(user.get("full_name")), String.valueOf(user.get("email")),
                request.paymentMethod(), trimToNull(request.paymentReference()), now));
        return new UpgradeRequestResponse(id, userId, "premium", request.paymentMethod(),
                trimToNull(request.paymentReference()), "pending", now);
    }

    @Transactional
    public UpgradeRequestResponse approve(UUID requestId, UUID userId, String suppliedKey) {
        if (adminKey == null || adminKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Manual billing approval is not configured on this server");
        }
        if (suppliedKey == null || !MessageDigest.isEqual(adminKey.getBytes(), suppliedKey.getBytes())) {
            throw new AccessDeniedException("Invalid billing administrator key");
        }
        Instant reviewedAt = clock.instant();
        UUID approved = jdbcTemplate.queryForObject("select fn_approve_upgrade(?, ?, ?)", UUID.class,
                requestId, userId, Timestamp.from(reviewedAt));
        if (approved == null) {
            throw new ConflictException("The pending upgrade request could not be approved");
        }
        setUser(userId);
        return recentRequests(userId, 1).stream().findFirst()
                .orElseThrow(() -> new ConflictException("The upgrade request was approved but could not be loaded"));
    }

    private List<UpgradeRequestResponse> recentRequests(UUID userId, int limit) {
        return jdbcTemplate.query("""
                select id, user_id, plan_code, payment_method, payment_reference, status, requested_at
                from upgrade_requests where user_id = ? order by requested_at desc limit ?
                """, (rs, rowNum) -> new UpgradeRequestResponse(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("plan_code"), rs.getString("payment_method"),
                rs.getString("payment_reference"), rs.getString("status"),
                rs.getTimestamp("requested_at").toInstant()), userId, limit);
    }

    private void setUser(UUID userId) {
        jdbcTemplate.queryForObject("select set_config('app.user_id', ?, true)", String.class, userId.toString());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
