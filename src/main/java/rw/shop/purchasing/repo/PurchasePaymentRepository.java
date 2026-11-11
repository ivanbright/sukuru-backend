package rw.shop.purchasing.repo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.purchasing.domain.PurchasePayment;

public interface PurchasePaymentRepository extends JpaRepository<PurchasePayment, UUID> {

    List<PurchasePayment> findByPurchaseIdOrderByPaidAtAsc(UUID purchaseId);

    @Query("""
            select coalesce(sum(p.amount), 0) from PurchasePayment p
            where p.businessId = :businessId and p.purchaseId = :purchaseId
            """)
    BigDecimal sumForPurchase(@Param("businessId") UUID businessId, @Param("purchaseId") UUID purchaseId);
}
