package rw.shop.parties.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.common.Ids;
import rw.shop.common.error.NotFoundException;
import rw.shop.common.pagination.PageRequests;
import rw.shop.common.pagination.PageResponse;
import rw.shop.common.web.TenantContext;
import rw.shop.parties.api.SupplierRequest;
import rw.shop.parties.api.SupplierResponse;
import rw.shop.parties.domain.Supplier;
import rw.shop.parties.repo.SupplierRepository;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final Clock clock;

    public SupplierService(SupplierRepository supplierRepository, Clock clock) {
        this.supplierRepository = supplierRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(String search, int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        String query = search == null || search.isBlank() ? "" : search.trim();
        Page<Supplier> suppliers = supplierRepository.search(businessId, query,
                PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        return new PageResponse<>(suppliers.getContent().stream().map(this::toResponse).toList(),
                suppliers.getNumber(), suppliers.getSize(), suppliers.getTotalElements());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return toResponse(find(id));
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Supplier supplier = new Supplier();
        supplier.setId(Ids.newId());
        supplier.setBusinessId(TenantContext.requireBusinessId());
        supplier.setName(request.name().trim());
        supplier.setPhone(trimToNull(request.phone()));
        supplier.setEmail(trimToNull(request.email()));
        supplier.setAddress(trimToNull(request.address()));
        supplier.setIsActive(true);
        supplier.setCreatedAt(clock.instant());
        supplierRepository.save(supplier);
        return toResponse(supplier);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = find(id);
        supplier.setName(request.name().trim());
        supplier.setPhone(trimToNull(request.phone()));
        supplier.setEmail(trimToNull(request.email()));
        supplier.setAddress(trimToNull(request.address()));
        supplierRepository.save(supplier);
        return toResponse(supplier);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public void delete(UUID id) {
        Supplier supplier = find(id);
        supplier.setIsActive(false);
        supplierRepository.save(supplier);
    }

    private Supplier find(UUID id) {
        return supplierRepository.findByIdAndBusinessId(id, TenantContext.requireBusinessId())
                .orElseThrow(() -> new NotFoundException("Supplier not found"));
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getPhone(),
                supplier.getEmail(), supplier.getAddress(), supplier.getCreatedAt());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
