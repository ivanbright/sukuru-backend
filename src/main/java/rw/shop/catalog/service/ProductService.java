package rw.shop.catalog.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.audit.service.AuditService;
import rw.shop.catalog.api.ProductRequest;
import rw.shop.catalog.api.ProductResponse;
import rw.shop.catalog.domain.Product;
import rw.shop.catalog.repo.CategoryRepository;
import rw.shop.catalog.repo.ProductRepository;
import rw.shop.common.Ids;
import rw.shop.common.Money;
import rw.shop.common.error.ConflictException;
import rw.shop.common.error.NotFoundException;
import rw.shop.common.pagination.PageRequests;
import rw.shop.common.pagination.PageResponse;
import rw.shop.common.web.TenantContext;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
            AuditService auditService, Clock clock) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String search, Boolean isActive, int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        String query = search == null || search.isBlank() ? "" : search.trim();
        Boolean active = isActive == null ? Boolean.TRUE : isActive;
        Page<Product> products = productRepository.search(businessId, query, active,
                PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        boolean includeCost = !isStaff();
        return new PageResponse<>(products.getContent().stream()
                .map(product -> toResponse(product, includeCost)).toList(),
                products.getNumber(), products.getSize(), products.getTotalElements());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(find(id), !isStaff());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public ProductResponse create(ProductRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        String sku = trimToNull(request.sku());
        if (sku != null && productRepository.existsByBusinessIdAndSkuIgnoreCase(businessId, sku)) {
            throw new ConflictException("SKU already exists in this business");
        }
        validateCategory(request.categoryId(), businessId);
        BigDecimal purchasePrice = optionalMoney(request.purchasePrice(), "purchasePrice");
        BigDecimal sellingPrice = optionalMoney(request.sellingPrice(), "sellingPrice");

        Instant now = clock.instant();
        Product product = new Product();
        product.setId(Ids.newId());
        product.setBusinessId(businessId);
        product.setName(request.name().trim());
        product.setSku(sku);
        product.setDescription(request.description());
        product.setUnit(request.unit().trim());
        product.setCategoryId(request.categoryId());
        product.setPurchasePrice(purchasePrice);
        product.setSellingPrice(sellingPrice);
        product.setIsActive(request.isActive() == null ? Boolean.TRUE : request.isActive());
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productRepository.save(product);
        auditService.record("product.create", "product", product.getId(), null, snapshot(product));
        return toResponse(product, true);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        Product product = find(id);
        String sku = trimToNull(request.sku());
        if (sku != null && productRepository.existsByBusinessIdAndSkuIgnoreCaseAndIdNot(businessId, sku, id)) {
            throw new ConflictException("SKU already exists in this business");
        }
        validateCategory(request.categoryId(), businessId);
        Map<String, Object> before = snapshot(product);

        product.setName(request.name().trim());
        product.setSku(sku);
        product.setDescription(request.description());
        product.setUnit(request.unit().trim());
        product.setCategoryId(request.categoryId());
        product.setPurchasePrice(optionalMoney(request.purchasePrice(), "purchasePrice"));
        product.setSellingPrice(optionalMoney(request.sellingPrice(), "sellingPrice"));
        if (request.isActive() != null) {
            product.setIsActive(request.isActive());
        }
        product.setUpdatedAt(clock.instant());
        productRepository.save(product);
        auditService.record("product.update", "product", product.getId(), before, snapshot(product));
        return toResponse(product, true);
    }

    private void validateCategory(UUID categoryId, UUID businessId) {
        if (categoryId != null && categoryRepository.findByIdAndBusinessId(categoryId, businessId).isEmpty()) {
            throw new NotFoundException("Category not found");
        }
    }

    private Product find(UUID id) {
        return productRepository.findByIdAndBusinessId(id, TenantContext.requireBusinessId())
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private BigDecimal optionalMoney(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        BigDecimal amount = Money.parse(value, field);
        Money.requireNonNegative(amount, field);
        return amount;
    }

    private ProductResponse toResponse(Product product, boolean includeCost) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getUnit(),
                product.getCategoryId(),
                includeCost && product.getPurchasePrice() != null ? Money.format(product.getPurchasePrice()) : null,
                product.getSellingPrice() == null ? null : Money.format(product.getSellingPrice()),
                Boolean.TRUE.equals(product.getIsActive()),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    private Map<String, Object> snapshot(Product product) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", product.getName());
        data.put("sku", product.getSku());
        data.put("description", product.getDescription());
        data.put("unit", product.getUnit());
        data.put("categoryId", product.getCategoryId() == null ? null : product.getCategoryId().toString());
        data.put("purchasePrice", product.getPurchasePrice() == null ? null : Money.format(product.getPurchasePrice()));
        data.put("sellingPrice", product.getSellingPrice() == null ? null : Money.format(product.getSellingPrice()));
        data.put("isActive", Boolean.TRUE.equals(product.getIsActive()));
        return data;
    }

    private boolean isStaff() {
        return "staff".equals(TenantContext.role());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
