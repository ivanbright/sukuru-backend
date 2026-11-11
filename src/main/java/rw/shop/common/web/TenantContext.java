package rw.shop.common.web;

import java.util.UUID;

public final class TenantContext {

    public record Tenant(UUID businessId, UUID userId, UUID membershipId, String role) {
    }

    private static final ThreadLocal<Tenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Tenant tenant) {
        CURRENT.set(tenant);
    }

    public static Tenant get() {
        return CURRENT.get();
    }

    public static UUID businessId() {
        Tenant tenant = CURRENT.get();
        return tenant == null ? null : tenant.businessId();
    }

    public static UUID userId() {
        Tenant tenant = CURRENT.get();
        return tenant == null ? null : tenant.userId();
    }

    public static UUID membershipId() {
        Tenant tenant = CURRENT.get();
        return tenant == null ? null : tenant.membershipId();
    }

    public static String role() {
        Tenant tenant = CURRENT.get();
        return tenant == null ? null : tenant.role();
    }

    public static UUID requireBusinessId() {
        UUID businessId = businessId();
        if (businessId == null) {
            throw new IllegalStateException("No business in tenant context");
        }
        return businessId;
    }

    public static UUID requireUserId() {
        UUID userId = userId();
        if (userId == null) {
            throw new IllegalStateException("No user in tenant context");
        }
        return userId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
