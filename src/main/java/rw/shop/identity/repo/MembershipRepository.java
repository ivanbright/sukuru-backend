package rw.shop.identity.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import rw.shop.identity.domain.Membership;
import rw.shop.identity.domain.MembershipRole;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<Membership> findByBusinessIdAndUserId(UUID businessId, UUID userId);

    List<Membership> findByBusinessIdOrderByJoinedAtDesc(UUID businessId);

    Page<Membership> findByBusinessId(UUID businessId, Pageable pageable);

    long countByBusinessIdAndRoleAndIsActiveTrue(UUID businessId, MembershipRole role);
}
