package rw.shop.purchasing.api;

import java.time.Instant;
import java.util.UUID;

public record PurchasePaymentResponse(
        UUID id,
        String amount,
        String paymentMethod,
        String referenceNumber,
        Instant paidAt,
        Instant createdAt) {
}
