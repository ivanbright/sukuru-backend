package rw.shop.parties.api;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        Instant createdAt) {
}
