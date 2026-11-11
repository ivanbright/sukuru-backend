package rw.shop.purchasing.api;

import java.util.UUID;

public record PurchaseItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String quantity,
        String unitCost,
        String subtotal) {
}
