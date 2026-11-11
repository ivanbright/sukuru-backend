package rw.shop.billing.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ApproveUpgradeRequest(@NotNull UUID userId) {
}
