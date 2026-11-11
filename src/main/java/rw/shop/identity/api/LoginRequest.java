package rw.shop.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record LoginRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 200) String password,
        UUID businessId) {
}
