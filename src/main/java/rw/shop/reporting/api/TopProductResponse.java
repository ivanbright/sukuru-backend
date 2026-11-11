package rw.shop.reporting.api;

import java.util.UUID;

public record TopProductResponse(
        UUID productId,
        String name,
        String quantitySold,
        String revenue,
        String grossProfit) {
}
