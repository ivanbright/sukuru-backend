package rw.shop.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import rw.shop.audit.domain.AuditLog;
import rw.shop.audit.repo.AuditLogRepository;
import rw.shop.common.Ids;
import rw.shop.common.web.CorrelationIds;
import rw.shop.common.web.TenantContext;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void record(String action, String entityType, UUID entityId, Object before, Object after) {
        AuditLog entry = new AuditLog();
        entry.setId(Ids.newId());
        entry.setBusinessId(TenantContext.requireBusinessId());
        entry.setActorUserId(TenantContext.userId());
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBefore(toJson(before));
        entry.setAfter(toJson(after));
        entry.setCorrelationId(CorrelationIds.current());
        entry.setCreatedAt(clock.instant());
        auditLogRepository.save(entry);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize audit data", ex);
        }
    }
}
