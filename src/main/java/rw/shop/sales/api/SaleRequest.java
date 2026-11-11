package rw.shop.sales.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record SaleRequest(
        UUID customerId,
        String notes,
        @NotEmpty List<@Valid SaleItemRequest> items) {
}
