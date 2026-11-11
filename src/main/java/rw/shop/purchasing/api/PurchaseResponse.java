package rw.shop.purchasing.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        UUID supplierId,
        String supplierName,
        String referenceNumber,
        String status,
        String totalAmount,
        String paidAmount,
        String paymentStatus,
        String notes,
        Instant purchasedAt,
        Instant createdAt,
        List<PurchaseItemResponse> items) {
}
