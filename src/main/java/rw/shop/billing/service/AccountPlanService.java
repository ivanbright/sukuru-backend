package rw.shop.billing.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.common.error.ConflictException;

@Service
public class AccountPlanService {

    private final JdbcTemplate jdbcTemplate;

    public AccountPlanService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public void requireWorkspaceAllowance(UUID userId) {
        jdbcTemplate.queryForObject("select set_config('app.user_id', ?, true)", String.class, userId.toString());
        List<Map<String, Object>> plans = jdbcTemplate.queryForList(
                "select plan_code, status from account_plans where user_id = ?", userId);
        boolean premium = !plans.isEmpty()
                && "premium".equals(plans.get(0).get("plan_code"))
                && "active".equals(plans.get(0).get("status"));
        Integer workspaceCount = jdbcTemplate.queryForObject("""
                select count(*) from fn_user_memberships(?)
                where role = 'owner' and is_active = true
                """, Integer.class, userId);
        int limit = premium ? 5 : 1;
        if (workspaceCount != null && workspaceCount >= limit) {
            throw new ConflictException(premium
                    ? "The Premium plan includes up to 5 workspaces. Contact support to add more."
                    : "The Free plan includes 1 workspace. Upgrade to Premium to create up to 5 workspaces.");
        }
    }
}
