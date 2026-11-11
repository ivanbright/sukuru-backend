package rw.shop.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterBusinessRequest(
        @NotBlank @Size(max = 150) String businessName,
        @Size(max = 100) String businessType,
        @Size(max = 30) String phone,
        @Size(max = 100) String country,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 10, max = 200) String password) {
}
