package rw.shop.billing.service;

import java.time.Instant;
import java.util.UUID;

public record UpgradeRequestSubmitted(
        UUID requestId,
        UUID userId,
        String fullName,
        String email,
        String paymentMethod,
        String paymentReference,
        Instant requestedAt) {
}
