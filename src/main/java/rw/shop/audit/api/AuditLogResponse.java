package rw.shop.audit.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        JsonNode before,
        JsonNode after,
        String correlationId,
        Instant createdAt) {
}
