package rw.shop.billing.api;

import java.time.Instant;
import java.util.UUID;

public record UpgradeRequestResponse(
        UUID id,
        UUID userId,
        String plan,
        String paymentMethod,
        String paymentReference,
        String status,
        Instant requestedAt) {
}
