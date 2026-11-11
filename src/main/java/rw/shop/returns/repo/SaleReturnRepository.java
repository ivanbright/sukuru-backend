package rw.shop.returns.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.returns.domain.SaleReturn;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, UUID> {

    Optional<SaleReturn> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("""
            select r from SaleReturn r
            where r.businessId = :businessId
              and (:saleId is null or r.saleId = :saleId)
            """)
    Page<SaleReturn> search(@Param("businessId") UUID businessId, @Param("saleId") UUID saleId, Pageable pageable);
}
