package rw.shop.parties.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.parties.domain.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("""
            select s from Supplier s
            where s.businessId = :businessId
              and s.isActive = true
              and (:search = '' or lower(s.name) like lower(concat('%', :search, '%')))
            """)
    Page<Supplier> search(@Param("businessId") UUID businessId, @Param("search") String search, Pageable pageable);
}
