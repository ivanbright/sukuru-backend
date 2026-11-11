package rw.shop.identity.api;

import java.util.UUID;

public record RegisterBusinessResponse(UUID businessId, UUID userId, UUID membershipId) {
}
