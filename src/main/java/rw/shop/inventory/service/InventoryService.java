package rw.shop.inventory.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.audit.service.AuditService;
import rw.shop.catalog.domain.Product;
import rw.shop.catalog.repo.ProductRepository;
import rw.shop.common.Ids;
import rw.shop.common.Quantity;
import rw.shop.common.error.NotFoundException;
import rw.shop.common.error.ValidationException;
import rw.shop.common.pagination.PageRequests;
import rw.shop.common.pagination.PageResponse;
import rw.shop.common.web.TenantContext;
import rw.shop.inventory.api.AdjustmentRequest;
import rw.shop.inventory.api.MovementResponse;
import rw.shop.inventory.api.StockResponse;
import rw.shop.inventory.domain.InventoryMovement;
import rw.shop.inventory.domain.MovementType;
import rw.shop.inventory.repo.InventoryMovementRepository;

@Service
public class InventoryService {

    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;
    private final Clock clock;

    public InventoryService(InventoryMovementRepository movementRepository, ProductRepository productRepository,
            AuditService auditService, Clock clock) {
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public PageResponse<StockResponse> listStock(String search, int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        String query = search == null || search.isBlank() ? "" : search.trim();
        Page<Product> products = productRepository.search(businessId, query, null,
                PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        List<UUID> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<UUID, BigDecimal> quantities = productIds.isEmpty() ? Map.of() : sumByProduct(businessId, productIds);
        List<StockResponse> items = products.getContent().stream()
                .map(product -> new StockResponse(product.getId(), product.getName(), product.getSku(),
                        product.getUnit(), Quantity.format(quantities.getOrDefault(product.getId(), BigDecimal.ZERO))))
                .toList();
        return new PageResponse<>(items, products.getNumber(), products.getSize(), products.getTotalElements());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public MovementResponse adjust(AdjustmentRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        Product product = productRepository.findByIdAndBusinessId(request.productId(), businessId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        BigDecimal delta = Quantity.parse(request.quantityDelta(), "quantityDelta");
        Quantity.requireNonZero(delta, "quantityDelta");

        InventoryMovement movement = new InventoryMovement();
        movement.setId(Ids.newId());
        movement.setBusinessId(businessId);
        movement.setProductId(product.getId());
        movement.setMovementType(MovementType.adjustment);
        movement.setQuantityDelta(delta);
        movement.setReason(request.reason().trim());
        movement.setCreatedByUserId(TenantContext.requireUserId());
        movement.setCreatedAt(clock.instant());
        movementRepository.save(movement);

        Map<String, Object> after = new HashMap<>();
        after.put("productId", product.getId().toString());
        after.put("quantityDelta", Quantity.format(delta));
        after.put("reason", movement.getReason());
        auditService.record("inventory.adjustment", "inventory_movement", movement.getId(), null, after);

        return toResponse(movement, product.getName());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> listMovements(UUID productId, String type, Instant from, Instant to,
            int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        if (productId != null && productRepository.findByIdAndBusinessId(productId, businessId).isEmpty()) {
            throw new NotFoundException("Product not found");
        }
        MovementType movementType = parseType(type);
        Page<InventoryMovement> movements = movementRepository.search(businessId, productId, movementType, from, to,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<UUID> productIds = movements.getContent().stream().map(InventoryMovement::getProductId).distinct()
                .toList();
        Map<UUID, String> names = productIds.isEmpty() ? Map.of()
                : productRepository.findByBusinessIdAndIdIn(businessId, productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Product::getName));
        List<MovementResponse> items = movements.getContent().stream()
                .map(movement -> toResponse(movement, names.get(movement.getProductId()))).toList();
        return new PageResponse<>(items, movements.getNumber(), movements.getSize(), movements.getTotalElements());
    }

    private Map<UUID, BigDecimal> sumByProduct(UUID businessId, List<UUID> productIds) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (Object[] row : movementRepository.sumForProducts(businessId, productIds)) {
            result.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return result;
    }

    private MovementType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            String normalized = type.trim().toLowerCase(Locale.ROOT);
            return MovementType.valueOf("return".equals(normalized) ? "returning" : normalized);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("type", "must be one of purchase, sale, adjustment, return");
        }
    }

    private MovementResponse toResponse(InventoryMovement movement, String productName) {
        return new MovementResponse(
                movement.getId(),
                movement.getProductId(),
                productName,
                movement.getMovementType() == MovementType.returning ? "return" : movement.getMovementType().name(),
                Quantity.format(movement.getQuantityDelta()),
                movement.getReason(),
                movement.getCreatedByUserId(),
                movement.getCreatedAt());
    }
}
