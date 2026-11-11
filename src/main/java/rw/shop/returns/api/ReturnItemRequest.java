package rw.shop.returns.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ReturnItemRequest(
        @NotNull(message = "Sale item ID is required")
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        UUID saleItemId,

        @NotNull(message = "Product ID is required")
        @Schema(example = "660e8400-e29b-41d4-a716-446655440000")
        UUID productId,

        @NotNull(message = "Quantity is required")
        @Pattern(regexp = "^\\d{1,11}(\\.\\d{1,3})?$", message = "Quantity must be a positive number")
        @Schema(example = "2.000")
        String quantityReturned
) {
    @Positive
    public java.math.BigDecimal quantityReturnedValue() {
        return new java.math.BigDecimal(quantityReturned);
    }
}
