package rw.shop.inventory.api;

import java.time.Instant;
import java.util.UUID;

public record MovementResponse(
        UUID id,
        UUID productId,
        String productName,
        String type,
        String quantityDelta,
        String reason,
        UUID createdByUserId,
        Instant createdAt) {
}
