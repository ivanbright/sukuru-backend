package rw.shop.returns.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.returns.domain.SaleReturnItem;

public interface SaleReturnItemRepository extends JpaRepository<SaleReturnItem, UUID> {

    @Query("select i from SaleReturnItem i where i.saleReturnId = :saleReturnId and i.businessId = :businessId")
    List<SaleReturnItem> findBySaleReturnId(@Param("saleReturnId") UUID saleReturnId, @Param("businessId") UUID businessId);
}
