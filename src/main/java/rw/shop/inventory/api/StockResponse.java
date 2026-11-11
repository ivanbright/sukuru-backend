package rw.shop.inventory.api;

import java.util.UUID;

public record StockResponse(
        UUID productId,
        String name,
        String sku,
        String unit,
        String quantity) {
}
