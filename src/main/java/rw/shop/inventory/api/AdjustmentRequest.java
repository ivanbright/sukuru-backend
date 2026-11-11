package rw.shop.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AdjustmentRequest(
        @NotNull UUID productId,
        @NotBlank String quantityDelta,
        @NotBlank String reason) {
}
