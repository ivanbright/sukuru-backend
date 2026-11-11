package rw.shop.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ProductRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String sku,
        String description,
        @NotBlank @Size(max = 30) String unit,
        UUID categoryId,
        String purchasePrice,
        String sellingPrice,
        Boolean isActive) {
}
