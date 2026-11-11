package rw.shop.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rw.shop.identity.domain.MembershipRole;

public record CreateMembershipRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @NotBlank @Size(min = 10, max = 200) String password,
        @NotNull MembershipRole role) {
}
