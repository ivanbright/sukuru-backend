package rw.shop.returns.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnItemResponse(
        UUID id,
        UUID saleItemId,
        UUID productId,
        String productName,
        String quantityReturned,
        String unitPrice,
        String subtotal
) {
    public ReturnItemResponse(UUID id, UUID saleItemId, UUID productId, String productName,
                              BigDecimal quantityReturned, BigDecimal unitPrice, BigDecimal subtotal) {
        this(id, saleItemId, productId, productName, quantityReturned.toPlainString(),
                unitPrice.toPlainString(), subtotal.toPlainString());
    }
}
