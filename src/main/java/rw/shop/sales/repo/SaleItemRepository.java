package rw.shop.sales.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import rw.shop.sales.domain.SaleItem;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findBySaleIdOrderByCreatedAtAsc(UUID saleId);

    void deleteBySaleId(UUID saleId);
}
