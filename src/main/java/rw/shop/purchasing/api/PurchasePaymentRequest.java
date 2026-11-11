package rw.shop.purchasing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import rw.shop.common.PaymentMethod;

public record PurchasePaymentRequest(
        @NotBlank String amount,
        @NotNull PaymentMethod paymentMethod,
        String referenceNumber) {
}
