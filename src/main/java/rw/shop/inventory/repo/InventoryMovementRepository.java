package rw.shop.inventory.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.inventory.domain.InventoryMovement;
import rw.shop.inventory.domain.MovementType;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    @Query("""
            select coalesce(sum(m.quantityDelta), 0) from InventoryMovement m
            where m.businessId = :businessId and m.productId = :productId
            """)
    BigDecimal sumForProduct(@Param("businessId") UUID businessId, @Param("productId") UUID productId);

    @Query("""
            select m.productId, coalesce(sum(m.quantityDelta), 0) from InventoryMovement m
            where m.businessId = :businessId and m.productId in :productIds
            group by m.productId
            """)
    List<Object[]> sumForProducts(@Param("businessId") UUID businessId,
            @Param("productIds") Collection<UUID> productIds);

    @Query("""
            select m from InventoryMovement m
            where m.businessId = :businessId
              and (:productId is null or m.productId = :productId)
              and (:type is null or m.movementType = :type)
              and (:from is null or m.createdAt >= :from)
              and (:to is null or m.createdAt < :to)
            """)
    Page<InventoryMovement> search(@Param("businessId") UUID businessId,
            @Param("productId") UUID productId,
            @Param("type") MovementType type,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
