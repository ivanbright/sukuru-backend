package rw.shop.identity.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SwitchBusinessRequest(@NotNull UUID businessId) {
}
