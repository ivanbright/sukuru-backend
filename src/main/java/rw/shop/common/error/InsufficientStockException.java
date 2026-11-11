package rw.shop.common.error;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InsufficientStockException extends ApiException {

    public InsufficientStockException(UUID productId, BigDecimal available, BigDecimal requested) {
        super(ErrorCode.INSUFFICIENT_STOCK,
                "Insufficient stock for product " + productId,
                List.of(Map.of(
                        "productId", productId.toString(),
                        "available", available.toPlainString(),
                        "requested", requested.toPlainString())));
    }
}
