package rw.shop.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import rw.shop.identity.domain.MembershipRole;

public record MembershipResponse(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        String phone,
        MembershipRole role,
        boolean isActive,
        Instant joinedAt) {
}
