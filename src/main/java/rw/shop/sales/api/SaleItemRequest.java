package rw.shop.sales.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SaleItemRequest(
        @NotNull UUID productId,
        @NotBlank String quantity,
        String unitPrice) {
}
