package rw.shop.sales.repo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.sales.domain.SalePayment;

public interface SalePaymentRepository extends JpaRepository<SalePayment, UUID> {

    List<SalePayment> findBySaleIdOrderByPaidAtAsc(UUID saleId);

    @Query("""
            select coalesce(sum(p.amount), 0) from SalePayment p
            where p.businessId = :businessId and p.saleId = :saleId
            """)
    BigDecimal sumForSale(@Param("businessId") UUID businessId, @Param("saleId") UUID saleId);
}
