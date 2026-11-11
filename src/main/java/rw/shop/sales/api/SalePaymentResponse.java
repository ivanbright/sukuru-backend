package rw.shop.sales.api;

import java.time.Instant;
import java.util.UUID;

public record SalePaymentResponse(
        UUID id,
        String amount,
        String paymentMethod,
        String referenceNumber,
        Instant paidAt,
        Instant createdAt) {
}
