package rw.shop.parties.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.shop.parties.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("""
            select c from Customer c
            where c.businessId = :businessId
              and c.isActive = true
              and (:search = '' or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    Page<Customer> search(@Param("businessId") UUID businessId, @Param("search") String search, Pageable pageable);
}
