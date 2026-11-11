package rw.shop.sales.api;

import java.time.Instant;
import java.util.UUID;

public record SaleSummaryResponse(
        UUID id,
        String referenceNumber,
        UUID customerId,
        String customerName,
        String status,
        String totalAmount,
        String paidAmount,
        String paymentStatus,
        Instant soldAt,
        Instant createdAt) {
}
