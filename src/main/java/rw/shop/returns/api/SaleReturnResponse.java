package rw.shop.returns.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import rw.shop.returns.domain.RefundMethod;

public record SaleReturnResponse(
        UUID id,
        UUID saleId,
        String saleReference,
        UUID customerId,
        String customerName,
        String referenceNumber,
        String totalAmount,
        RefundMethod refundMethod,
        String notes,
        List<ReturnItemResponse> items,
        Instant returnedAt,
        Instant createdAt
) {
    public SaleReturnResponse(UUID id, UUID saleId, String saleReference, UUID customerId, String customerName,
                              String referenceNumber, BigDecimal totalAmount, RefundMethod refundMethod, String notes,
                              List<ReturnItemResponse> items, Instant returnedAt, Instant createdAt) {
        this(id, saleId, saleReference, customerId, customerName, referenceNumber,
                totalAmount.toPlainString(), refundMethod, notes, items, returnedAt, createdAt);
    }
}
