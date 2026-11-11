package rw.shop.business.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessRequest(
        @NotBlank(message = "Business name is required")
        @Size(max = 150, message = "Business name must not exceed 150 characters")
        @Schema(example = "Kigali General Store")
        String name,

        @Size(max = 100, message = "Business type must not exceed 100 characters")
        @Schema(example = "Retail")
        String businessType,

        @Size(max = 30, message = "Phone must not exceed 30 characters")
        @Schema(example = "+250788123456")
        String phone,

        @Email(message = "Email must be valid")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        @Schema(example = "info@kigalistore.rw")
        String email,

        @Schema(example = "KN 5 Ave, Kigali")
        String address,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        @Schema(example = "Rwanda")
        String country,

        @Size(max = 10, message = "Currency must not exceed 10 characters")
        @Schema(example = "RWF")
        String currency
) {
}
