package rw.shop.identity.domain;

import java.util.UUID;

public record MembershipSummary(UUID businessId, String businessName, MembershipRole role, boolean active) {
}
