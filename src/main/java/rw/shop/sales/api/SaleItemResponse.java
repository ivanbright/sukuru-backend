package rw.shop.sales.api;

import java.util.UUID;

public record SaleItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String quantity,
        String unitPrice,
        String unitCost,
        String subtotal) {
}
