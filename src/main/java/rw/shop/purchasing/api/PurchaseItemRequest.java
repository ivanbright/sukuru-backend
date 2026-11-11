package rw.shop.purchasing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PurchaseItemRequest(
        @NotNull UUID productId,
        @NotBlank String quantity,
        @NotBlank String unitCost) {
}
