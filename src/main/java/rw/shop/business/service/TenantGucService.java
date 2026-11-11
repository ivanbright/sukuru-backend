package rw.shop.business.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TenantGucService {

    @PersistenceContext
    private EntityManager entityManager;

    public void setBusiness(UUID businessId) {
        entityManager.createNativeQuery("select set_config('app.business_id', :id, true)")
                .setParameter("id", businessId.toString())
                .getSingleResult();
    }
}
