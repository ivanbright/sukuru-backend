package rw.shop.purchasing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
import rw.shop.common.Money;
import rw.shop.common.Quantity;
import rw.shop.common.error.ConflictException;
import rw.shop.common.error.NotFoundException;
import rw.shop.common.error.ValidationException;
import rw.shop.common.pagination.PageRequests;
import rw.shop.common.pagination.PageResponse;
import rw.shop.common.web.TenantContext;
import rw.shop.inventory.domain.InventoryMovement;
import rw.shop.inventory.domain.MovementType;
import rw.shop.inventory.repo.InventoryMovementRepository;
import rw.shop.parties.domain.Supplier;
import rw.shop.parties.repo.SupplierRepository;
import rw.shop.purchasing.api.PurchaseItemRequest;
import rw.shop.purchasing.api.PurchaseItemResponse;
import rw.shop.purchasing.api.PurchasePaymentRequest;
import rw.shop.purchasing.api.PurchasePaymentResponse;
import rw.shop.purchasing.api.PurchaseRequest;
import rw.shop.purchasing.api.PurchaseResponse;
import rw.shop.purchasing.api.PurchaseSummaryResponse;
import rw.shop.purchasing.domain.Purchase;
import rw.shop.purchasing.domain.PurchaseItem;
import rw.shop.purchasing.domain.PurchasePayment;
import rw.shop.purchasing.domain.PurchaseStatus;
import rw.shop.purchasing.repo.PurchaseItemRepository;
import rw.shop.purchasing.repo.PurchasePaymentRepository;
import rw.shop.purchasing.repo.PurchaseRepository;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final PurchasePaymentRepository purchasePaymentRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryMovementRepository movementRepository;
    private final AuditService auditService;
    private final Clock clock;

    public PurchaseService(PurchaseRepository purchaseRepository, PurchaseItemRepository purchaseItemRepository,
            PurchasePaymentRepository purchasePaymentRepository, ProductRepository productRepository,
            SupplierRepository supplierRepository, InventoryMovementRepository movementRepository,
            AuditService auditService, Clock clock) {
        this.purchaseRepository = purchaseRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.purchasePaymentRepository = purchasePaymentRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.movementRepository = movementRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional(readOnly = true)
    public PageResponse<PurchaseSummaryResponse> list(int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        Page<Purchase> purchases = purchaseRepository.findByBusinessId(businessId,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Set<UUID> supplierIds = purchases.getContent().stream().map(Purchase::getSupplierId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> supplierNames = supplierIds.isEmpty() ? Map.of()
                : supplierRepository.findAllById(supplierIds).stream()
                        .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
        List<PurchaseSummaryResponse> items = purchases.getContent().stream()
                .map(purchase -> {
                    BigDecimal paid = purchasePaymentRepository.sumForPurchase(businessId, purchase.getId());
                    return toSummary(purchase, supplierNames.get(purchase.getSupplierId()), paid);
                }).toList();
        return new PageResponse<>(items, purchases.getNumber(), purchases.getSize(), purchases.getTotalElements());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional(readOnly = true)
    public PurchaseResponse get(UUID id) {
        UUID businessId = TenantContext.requireBusinessId();
        Purchase purchase = purchaseRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Purchase not found"));
        return toResponse(purchase, businessId);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public PurchaseResponse create(PurchaseRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        Supplier supplier = resolveSupplier(request.supplierId(), businessId);
        String reference = trimToNull(request.referenceNumber());
        if (reference != null && purchaseRepository.existsByBusinessIdAndReferenceNumber(businessId, reference)) {
            throw new ConflictException("Reference number already exists in this business");
        }
        Map<UUID, Product> products = loadProducts(businessId, request.items());
        Instant now = clock.instant();
        UUID purchaseId = Ids.newId();

        List<PurchaseItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseItemRequest line : request.items()) {
            PurchaseItem item = buildItem(businessId, purchaseId, line, now);
            items.add(item);
            total = total.add(item.getSubtotal());
        }

        Purchase purchase = new Purchase();
        purchase.setId(purchaseId);
        purchase.setBusinessId(businessId);
        purchase.setSupplierId(supplier == null ? null : supplier.getId());
        purchase.setCreatedByUserId(TenantContext.requireUserId());
        purchase.setReferenceNumber(reference);
        purchase.setStatus(PurchaseStatus.draft);
        purchase.setTotalAmount(Money.of(total));
        purchase.setNotes(trimToNull(request.notes()));
        purchase.setCreatedAt(now);
        purchaseRepository.save(purchase);
        purchaseItemRepository.saveAll(items);

        auditService.record("purchase.create", "purchase", purchase.getId(), null, snapshot(purchase));
        return toResponse(purchase, items, supplier == null ? null : supplier.getName(), BigDecimal.ZERO, products);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public PurchaseResponse update(UUID id, PurchaseRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        Purchase purchase = lockPurchase(id, businessId);
        if (purchase.getStatus() != PurchaseStatus.draft) {
            throw new ConflictException("Only draft purchases can be modified");
        }
        Supplier supplier = resolveSupplier(request.supplierId(), businessId);
        String reference = trimToNull(request.referenceNumber());
        if (reference != null && purchaseRepository.existsByBusinessIdAndReferenceNumberAndIdNot(businessId,
                reference, id)) {
            throw new ConflictException("Reference number already exists in this business");
        }
        Map<UUID, Product> products = loadProducts(businessId, request.items());
        Map<String, Object> before = snapshot(purchase);
        Instant now = clock.instant();

        List<PurchaseItem> existing = purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(id);
        purchaseItemRepository.deleteAll(existing);

        List<PurchaseItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseItemRequest line : request.items()) {
            PurchaseItem item = buildItem(businessId, id, line, now);
            items.add(item);
            total = total.add(item.getSubtotal());
        }
        purchaseItemRepository.saveAll(items);

        purchase.setSupplierId(supplier == null ? null : supplier.getId());
        purchase.setReferenceNumber(reference);
        purchase.setNotes(trimToNull(request.notes()));
        purchase.setTotalAmount(Money.of(total));
        purchaseRepository.save(purchase);

        auditService.record("purchase.update", "purchase", purchase.getId(), before, snapshot(purchase));
        return toResponse(purchase, items, supplier == null ? null : supplier.getName(), BigDecimal.ZERO, products);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public PurchaseResponse receive(UUID id) {
        UUID businessId = TenantContext.requireBusinessId();
        Purchase purchase = lockPurchase(id, businessId);
        if (purchase.getStatus() != PurchaseStatus.draft) {
            throw new ConflictException("Only draft purchases can be received");
        }
        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(id);
        if (items.isEmpty()) {
            throw new ConflictException("Purchase has no items");
        }
        Set<UUID> productIds = items.stream().map(PurchaseItem::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> products = productRepository.findByBusinessIdAndIdIn(businessId, productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.size() != productIds.size()) {
            throw new NotFoundException("Product not found");
        }
        Instant now = clock.instant();
        UUID userId = TenantContext.requireUserId();
        Map<String, Object> before = snapshot(purchase);

        for (PurchaseItem item : items) {
            InventoryMovement movement = new InventoryMovement();
            movement.setId(Ids.newId());
            movement.setBusinessId(businessId);
            movement.setProductId(item.getProductId());
            movement.setMovementType(MovementType.purchase);
            movement.setQuantityDelta(item.getQuantity());
            movement.setPurchaseItemId(item.getId());
            movement.setCreatedByUserId(userId);
            movement.setCreatedAt(now);
            movementRepository.save(movement);

            Product product = products.get(item.getProductId());
            product.setPurchasePrice(item.getUnitCost());
            product.setUpdatedAt(now);
            productRepository.save(product);
        }

        purchase.setStatus(PurchaseStatus.received);
        purchase.setPurchasedAt(now);
        purchaseRepository.save(purchase);
        auditService.record("purchase.receive", "purchase", purchase.getId(), before, snapshot(purchase));

        Supplier supplier = purchase.getSupplierId() == null ? null
                : supplierRepository.findByIdAndBusinessId(purchase.getSupplierId(), businessId).orElse(null);
        return toResponse(purchase, items, supplier == null ? null : supplier.getName(), BigDecimal.ZERO, products);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public PurchaseResponse cancel(UUID id) {
        UUID businessId = TenantContext.requireBusinessId();
        Purchase purchase = lockPurchase(id, businessId);
        if (purchase.getStatus() != PurchaseStatus.draft) {
            throw new ConflictException("Only draft purchases can be cancelled");
        }
        Map<String, Object> before = snapshot(purchase);
        purchase.setStatus(PurchaseStatus.cancelled);
        purchaseRepository.save(purchase);
        auditService.record("purchase.cancel", "purchase", purchase.getId(), before, snapshot(purchase));
        return toResponse(purchase, businessId);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    public PurchasePaymentResponse addPayment(UUID id, PurchasePaymentRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        Purchase purchase = lockPurchase(id, businessId);
        if (purchase.getStatus() != PurchaseStatus.received) {
            throw new ConflictException("Only received purchases can be paid");
        }
        BigDecimal amount = Money.parse(request.amount(), "amount");
        Money.requirePositive(amount, "amount");
        BigDecimal paid = purchasePaymentRepository.sumForPurchase(businessId, id);
        if (paid.add(amount).compareTo(purchase.getTotalAmount()) > 0) {
            throw new ValidationException("amount", "would exceed the purchase total");
        }
        Instant now = clock.instant();
        PurchasePayment payment = new PurchasePayment();
        payment.setId(Ids.newId());
        payment.setBusinessId(businessId);
        payment.setPurchaseId(id);
        payment.setAmount(amount);
        payment.setPaymentMethod(request.paymentMethod());
        payment.setReferenceNumber(trimToNull(request.referenceNumber()));
        payment.setPaidAt(now);
        payment.setCreatedAt(now);
        purchasePaymentRepository.save(payment);

        Map<String, Object> after = new HashMap<>();
        after.put("amount", Money.format(amount));
        after.put("paymentMethod", request.paymentMethod().name());
        auditService.record("purchase.payment.add", "purchase_payment", payment.getId(), null, after);
        return toPaymentResponse(payment);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional(readOnly = true)
    public List<PurchasePaymentResponse> listPayments(UUID id) {
        UUID businessId = TenantContext.requireBusinessId();
        purchaseRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Purchase not found"));
        return purchasePaymentRepository.findByPurchaseIdOrderByPaidAtAsc(id).stream()
                .map(this::toPaymentResponse).toList();
    }

    private Purchase lockPurchase(UUID id, UUID businessId) {
        return purchaseRepository.findForUpdate(id, businessId)
                .orElseThrow(() -> new NotFoundException("Purchase not found"));
    }

    private Supplier resolveSupplier(UUID supplierId, UUID businessId) {
        if (supplierId == null) {
            return null;
        }
        return supplierRepository.findByIdAndBusinessId(supplierId, businessId)
                .orElseThrow(() -> new NotFoundException("Supplier not found"));
    }

    private Map<UUID, Product> loadProducts(UUID businessId, List<PurchaseItemRequest> lines) {
        Set<UUID> ids = lines.stream().map(PurchaseItemRequest::productId).collect(Collectors.toSet());
        Map<UUID, Product> products = productRepository.findByBusinessIdAndIdIn(businessId, ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.size() != ids.size()) {
            throw new NotFoundException("Product not found");
        }
        return products;
    }

    private PurchaseItem buildItem(UUID businessId, UUID purchaseId, PurchaseItemRequest line, Instant now) {
        BigDecimal quantity = Quantity.parse(line.quantity(), "quantity");
        Quantity.requirePositive(quantity, "quantity");
        BigDecimal unitCost = Money.parse(line.unitCost(), "unitCost");
        Money.requireNonNegative(unitCost, "unitCost");

        PurchaseItem item = new PurchaseItem();
        item.setId(Ids.newId());
        item.setBusinessId(businessId);
        item.setPurchaseId(purchaseId);
        item.setProductId(line.productId());
        item.setQuantity(quantity);
        item.setUnitCost(unitCost);
        item.setSubtotal(Money.multiply(quantity, unitCost));
        item.setCreatedAt(now);
        return item;
    }

    private PurchaseResponse toResponse(Purchase purchase, UUID businessId) {
        Supplier supplier = purchase.getSupplierId() == null ? null
                : supplierRepository.findByIdAndBusinessId(purchase.getSupplierId(), businessId).orElse(null);
        BigDecimal paid = purchasePaymentRepository.sumForPurchase(businessId, purchase.getId());
        return toResponse(purchase, businessId, supplier == null ? null : supplier.getName(), paid);
    }

    private PurchaseResponse toResponse(Purchase purchase, UUID businessId, String supplierName, BigDecimal paid) {
        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchase.getId());
        Set<UUID> productIds = items.stream().map(PurchaseItem::getProductId).collect(Collectors.toSet());
        Map<UUID, String> productNames = productIds.isEmpty() ? Map.of()
                : productRepository.findByBusinessIdAndIdIn(businessId, productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Product::getName));
        return toResponse(purchase, items, supplierName, paid, productNames);
    }

    private PurchaseResponse toResponse(Purchase purchase, List<PurchaseItem> items, String supplierName,
            BigDecimal paid, Map<UUID, ?> products) {
        Map<UUID, String> productNames = new HashMap<>();
        for (Map.Entry<UUID, ?> entry : products.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Product product) {
                productNames.put(product.getId(), product.getName());
            } else if (value instanceof String name) {
                productNames.put(entry.getKey(), name);
            }
        }
        List<PurchaseItemResponse> itemResponses = items.stream()
                .map(item -> new PurchaseItemResponse(item.getId(), item.getProductId(),
                        productNames.get(item.getProductId()), Quantity.format(item.getQuantity()),
                        Money.format(item.getUnitCost()), Money.format(item.getSubtotal())))
                .toList();
        return new PurchaseResponse(purchase.getId(), purchase.getSupplierId(), supplierName,
                purchase.getReferenceNumber(), purchase.getStatus().name(),
                Money.format(purchase.getTotalAmount()), Money.format(paid),
                paymentStatus(paid, purchase.getTotalAmount()), purchase.getNotes(), purchase.getPurchasedAt(),
                purchase.getCreatedAt(), itemResponses);
    }

    private PurchaseSummaryResponse toSummary(Purchase purchase, String supplierName, BigDecimal paid) {
        return new PurchaseSummaryResponse(purchase.getId(), purchase.getSupplierId(), supplierName,
                purchase.getReferenceNumber(), purchase.getStatus().name(),
                Money.format(purchase.getTotalAmount()), Money.format(paid),
                paymentStatus(paid, purchase.getTotalAmount()), purchase.getPurchasedAt(), purchase.getCreatedAt());
    }

    private String paymentStatus(BigDecimal paid, BigDecimal total) {
        if (paid.signum() == 0) {
            return "unpaid";
        }
        return paid.compareTo(total) < 0 ? "partial" : "paid";
    }

    private PurchasePaymentResponse toPaymentResponse(PurchasePayment payment) {
        return new PurchasePaymentResponse(payment.getId(), Money.format(payment.getAmount()),
                payment.getPaymentMethod().name(), payment.getReferenceNumber(), payment.getPaidAt(),
                payment.getCreatedAt());
    }

    private Map<String, Object> snapshot(Purchase purchase) {
        Map<String, Object> data = new HashMap<>();
        data.put("supplierId", purchase.getSupplierId() == null ? null : purchase.getSupplierId().toString());
        data.put("referenceNumber", purchase.getReferenceNumber());
        data.put("status", purchase.getStatus() == null ? null : purchase.getStatus().name());
        data.put("totalAmount", Money.format(purchase.getTotalAmount()));
        data.put("notes", purchase.getNotes());
        return data;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
