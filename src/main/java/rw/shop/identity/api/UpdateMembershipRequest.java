package rw.shop.identity.api;

import jakarta.validation.constraints.NotNull;
import rw.shop.identity.domain.MembershipRole;

public record UpdateMembershipRequest(@NotNull MembershipRole role, @NotNull Boolean isActive) {
}
