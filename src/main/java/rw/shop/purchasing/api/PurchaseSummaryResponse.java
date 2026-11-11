package rw.shop.purchasing.api;

import java.time.Instant;
import java.util.UUID;

public record PurchaseSummaryResponse(
        UUID id,
        UUID supplierId,
        String supplierName,
        String referenceNumber,
        String status,
        String totalAmount,
        String paidAmount,
        String paymentStatus,
        Instant purchasedAt,
        Instant createdAt) {
}
