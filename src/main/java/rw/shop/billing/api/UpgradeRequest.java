package rw.shop.billing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpgradeRequest(
        @NotBlank @Pattern(regexp = "mobile_money|email") String paymentMethod,
        @Size(max = 120) String paymentReference) {
}
