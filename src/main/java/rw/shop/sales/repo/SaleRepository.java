package rw.shop.sales.repo;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.sales.domain.Sale;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByIdAndBusinessId(UUID id, UUID businessId);

    Page<Sale> findByBusinessId(UUID businessId, Pageable pageable);

    Page<Sale> findByBusinessIdAndCreatedByUserId(UUID businessId, UUID createdByUserId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id and s.businessId = :businessId")
    Optional<Sale> findForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);
}
