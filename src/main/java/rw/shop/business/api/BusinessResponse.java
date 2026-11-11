package rw.shop.business.api;

import java.time.Instant;
import java.util.UUID;

public record BusinessResponse(
        UUID id,
        String name,
        String businessType,
        String phone,
        String email,
        String address,
        String country,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}
