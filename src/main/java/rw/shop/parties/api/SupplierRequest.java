package rw.shop.parties.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email,
        String address) {
}
