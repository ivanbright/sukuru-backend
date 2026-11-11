package rw.shop.catalog.api;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String sku,
        String description,
        String unit,
        UUID categoryId,
        String purchasePrice,
        String sellingPrice,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt) {
}
