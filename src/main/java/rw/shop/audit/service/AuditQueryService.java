package rw.shop.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.audit.api.AuditLogResponse;
import rw.shop.audit.domain.AuditLog;
import rw.shop.audit.repo.AuditLogRepository;
import rw.shop.common.pagination.PageRequests;
import rw.shop.common.pagination.PageResponse;
import rw.shop.common.web.TenantContext;

@Service
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditQueryService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(String entityType, UUID entityId, String action, UUID actorUserId,
            Instant from, Instant to, int page, int size) {
        Page<AuditLog> logs = auditLogRepository.search(TenantContext.requireBusinessId(),
                trimToNull(entityType), entityId, trimToNull(action), actorUserId, from, to,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(logs.getContent().stream().map(this::toResponse).toList(),
                logs.getNumber(), logs.getSize(), logs.getTotalElements());
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getAction(), log.getEntityType(),
                log.getEntityId(), parse(log.getBefore()), parse(log.getAfter()), log.getCorrelationId(),
                log.getCreatedAt());
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not parse audit data", ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
