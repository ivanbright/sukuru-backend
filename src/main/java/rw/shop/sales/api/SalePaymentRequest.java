package rw.shop.sales.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import rw.shop.common.PaymentMethod;

public record SalePaymentRequest(
        @NotBlank String amount,
        @NotNull PaymentMethod paymentMethod,
        String referenceNumber) {
}
