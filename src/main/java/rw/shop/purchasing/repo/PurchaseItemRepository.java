package rw.shop.purchasing.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import rw.shop.purchasing.domain.PurchaseItem;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, UUID> {

    List<PurchaseItem> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);

    void deleteByPurchaseId(UUID purchaseId);
}
