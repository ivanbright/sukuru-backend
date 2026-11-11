package rw.shop.catalog.repo;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.catalog.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.businessId = :businessId")
    Optional<Product> findForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);

    List<Product> findByBusinessIdAndIdIn(UUID businessId, Collection<UUID> ids);

    boolean existsByBusinessIdAndSkuIgnoreCase(UUID businessId, String sku);

    boolean existsByBusinessIdAndSkuIgnoreCaseAndIdNot(UUID businessId, String sku, UUID id);

    @Query("""
            select p from Product p
            where p.businessId = :businessId
              and (:search = ''
                   or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(coalesce(p.sku, '')) like lower(concat('%', :search, '%')))
              and (:active is null or p.isActive = :active)
            """)
    Page<Product> search(@Param("businessId") UUID businessId, @Param("search") String search,
            @Param("active") Boolean active, Pageable pageable);
}
