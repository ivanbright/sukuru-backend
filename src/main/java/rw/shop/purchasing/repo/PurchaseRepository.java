package rw.shop.purchasing.repo;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.purchasing.domain.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    Optional<Purchase> findByIdAndBusinessId(UUID id, UUID businessId);

    Page<Purchase> findByBusinessId(UUID businessId, Pageable pageable);

    boolean existsByBusinessIdAndReferenceNumber(UUID businessId, String referenceNumber);

    boolean existsByBusinessIdAndReferenceNumberAndIdNot(UUID businessId, String referenceNumber, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id and p.businessId = :businessId")
    Optional<Purchase> findForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);
}
