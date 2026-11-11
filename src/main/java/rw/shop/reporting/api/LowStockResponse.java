package rw.shop.reporting.api;

import java.util.UUID;

public record LowStockResponse(
        UUID productId,
        String name,
        String sku,
        String unit,
        String quantity) {
}
