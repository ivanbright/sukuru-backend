package rw.shop.catalog.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.catalog.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("""
            select c from Category c
            where c.businessId = :businessId
              and c.isActive = true
              and (:search = '' or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    Page<Category> search(@Param("businessId") UUID businessId, @Param("search") String search, Pageable pageable);
}
