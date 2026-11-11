package rw.shop.catalog.api;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, String description, Instant createdAt) {
}
