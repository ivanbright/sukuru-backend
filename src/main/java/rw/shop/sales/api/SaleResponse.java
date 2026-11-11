package rw.shop.sales.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        String referenceNumber,
        UUID customerId,
        String customerName,
        String status,
        String totalAmount,
        String paidAmount,
        String paymentStatus,
        String notes,
        Instant soldAt,
        Instant createdAt,
        List<SaleItemResponse> items) {
}
