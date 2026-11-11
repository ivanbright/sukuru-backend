package rw.shop.purchasing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record PurchaseRequest(
        UUID supplierId,
        String referenceNumber,
        String notes,
        @NotEmpty List<@Valid PurchaseItemRequest> items) {
}
