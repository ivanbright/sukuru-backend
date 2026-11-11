package rw.shop.business.repo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import rw.shop.business.domain.Business;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
}
