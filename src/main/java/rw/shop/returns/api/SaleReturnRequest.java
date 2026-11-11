package rw.shop.returns.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import rw.shop.returns.domain.RefundMethod;

public record SaleReturnRequest(
        @NotNull(message = "Sale ID is required")
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        UUID saleId,

        @NotEmpty(message = "At least one item must be returned")
        @Valid
        List<ReturnItemRequest> items,

        @Schema(example = "CASH", description = "Refund method - null for exchange only")
        RefundMethod refundMethod,

        @Schema(example = "Item damaged during delivery")
        String notes
) {
}
