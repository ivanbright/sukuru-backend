package rw.shop.audit.repo;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.audit.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            select a from AuditLog a
            where a.businessId = :businessId
              and (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (:action is null or a.action = :action)
              and (:actor is null or a.actorUserId = :actor)
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt < :to)
            """)
    Page<AuditLog> search(@Param("businessId") UUID businessId,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("action") String action,
            @Param("actor") UUID actor,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
