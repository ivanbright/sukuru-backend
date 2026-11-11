package rw.shop.sales.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
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
import rw.shop.common.error.InsufficientStockException;
import rw.shop.common.error.NotFoundException;
import rw.shop.common.error.ValidationException;
import rw.shop.common.pagination.PageRequests;
import rw.shop.common.pagination.PageResponse;
import rw.shop.common.web.TenantContext;
import rw.shop.inventory.domain.InventoryMovement;
import rw.shop.inventory.domain.MovementType;
import rw.shop.inventory.repo.InventoryMovementRepository;
import rw.shop.parties.domain.Customer;
import rw.shop.parties.repo.CustomerRepository;
import rw.shop.sales.api.SaleItemRequest;
import rw.shop.sales.api.SaleItemResponse;
import rw.shop.sales.api.SalePaymentRequest;
import rw.shop.sales.api.SalePaymentResponse;
import rw.shop.sales.api.SaleRequest;
import rw.shop.sales.api.SaleResponse;
import rw.shop.sales.api.SaleSummaryResponse;
import rw.shop.sales.domain.Sale;
import rw.shop.sales.domain.SaleItem;
import rw.shop.sales.domain.SalePayment;
import rw.shop.sales.domain.SaleStatus;
import rw.shop.sales.repo.SaleItemRepository;
import rw.shop.sales.repo.SalePaymentRepository;
import rw.shop.sales.repo.SaleRepository;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InventoryMovementRepository movementRepository;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SaleService(SaleRepository saleRepository, SaleItemRepository saleItemRepository,
            SalePaymentRepository salePaymentRepository, ProductRepository productRepository,
            CustomerRepository customerRepository, InventoryMovementRepository movementRepository,
            AuditService auditService, JdbcTemplate jdbcTemplate, Clock clock) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.salePaymentRepository = salePaymentRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.movementRepository = movementRepository;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public PageResponse<SaleSummaryResponse> list(int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        UUID createdBy = isStaff() ? TenantContext.requireUserId() : null;
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Sale> sales = createdBy == null
                ? saleRepository.findByBusinessId(businessId, pageable)
                : saleRepository.findByBusinessIdAndCreatedByUserId(businessId, createdBy, pageable);
        Set<UUID> customerIds = sales.getContent().stream().map(Sale::getCustomerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> customerNames = customerIds.isEmpty() ? Map.of()
                : customerRepository.findAllById(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, Customer::getName));
        List<SaleSummaryResponse> items = sales.getContent().stream()
                .map(sale -> {
                    BigDecimal paid = salePaymentRepository.sumForSale(businessId, sale.getId());
                    String customerName = sale.getCustomerId() == null ? null
                            : customerNames.get(sale.getCustomerId());
                    return toSummary(sale, customerName, paid);
                }).toList();
        return new PageResponse<>(items, sales.getNumber(), sales.getSize(), sales.getTotalElements());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        UUID businessId = TenantContext.requireBusinessId();
        Sale sale = saleRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
        ensureStaffOwns(sale);
        return toResponse(sale, businessId, !isStaff());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional
    public SaleResponse create(SaleRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        UUID userId = TenantContext.requireUserId();
        Customer customer = resolveCustomer(request.customerId(), businessId);
        Map<UUID, Product> products = lockProducts(businessId, request.items());
        Instant now = clock.instant();
        UUID saleId = Ids.newId();

        Map<UUID, BigDecimal> requested = new LinkedHashMap<>();
        for (SaleItemRequest line : request.items()) {
            BigDecimal quantity = Quantity.parse(line.quantity(), "quantity");
            Quantity.requirePositive(quantity, "quantity");
            requested.merge(line.productId(), quantity, BigDecimal::add);
        }
        for (Map.Entry<UUID, BigDecimal> entry : requested.entrySet()) {
            BigDecimal available = movementRepository.sumForProduct(businessId, entry.getKey());
            BigDecimal stock = available == null ? BigDecimal.ZERO : available;
            if (stock.compareTo(entry.getValue()) < 0) {
                throw new InsufficientStockException(entry.getKey(), stock, entry.getValue());
            }
        }

        List<SaleItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SaleItemRequest line : request.items()) {
            Product product = products.get(line.productId());
            BigDecimal quantity = Quantity.parse(line.quantity(), "quantity");
            BigDecimal unitPrice = resolveUnitPrice(line.unitPrice(), product);
            BigDecimal unitCost = product.getPurchasePrice() == null ? BigDecimal.ZERO.setScale(Money.SCALE)
                    : Money.of(product.getPurchasePrice());
            SaleItem item = new SaleItem();
            item.setId(Ids.newId());
            item.setBusinessId(businessId);
            item.setSaleId(saleId);
            item.setProductId(product.getId());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setUnitCost(unitCost);
            item.setSubtotal(Money.multiply(quantity, unitPrice));
            item.setCreatedAt(now);
            items.add(item);
            total = total.add(item.getSubtotal());
        }

        Sale sale = new Sale();
        sale.setId(saleId);
        sale.setBusinessId(businessId);
        sale.setCustomerId(customer == null ? null : customer.getId());
        sale.setCreatedByUserId(userId);
        sale.setReferenceNumber(nextSaleNumber(businessId));
        sale.setStatus(SaleStatus.completed);
        sale.setTotalAmount(Money.of(total));
        sale.setNotes(trimToNull(request.notes()));
        sale.setSoldAt(now);
        sale.setCreatedAt(now);
        saleRepository.saveAndFlush(sale);
        saleItemRepository.saveAllAndFlush(items);

        for (SaleItem item : items) {
            InventoryMovement movement = new InventoryMovement();
            movement.setId(Ids.newId());
            movement.setBusinessId(businessId);
            movement.setProductId(item.getProductId());
            movement.setMovementType(MovementType.sale);
            movement.setQuantityDelta(item.getQuantity().negate());
            movement.setSaleItemId(item.getId());
            movement.setCreatedByUserId(userId);
            movement.setCreatedAt(now);
            movementRepository.save(movement);
        }

        Map<String, Object> after = new HashMap<>();
        after.put("customerId", sale.getCustomerId() == null ? null : sale.getCustomerId().toString());
        after.put("status", sale.getStatus().name());
        after.put("totalAmount", Money.format(sale.getTotalAmount()));
        after.put("itemCount", items.size());
        auditService.record("sale.create", "sale", sale.getId(), null, after);

        Map<UUID, String> productNames = products.values().stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        return toResponse(sale, items, customer == null ? null : customer.getName(), BigDecimal.ZERO,
                productNames, !isStaff());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional
    public SalePaymentResponse addPayment(UUID id, SalePaymentRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        Sale sale = saleRepository.findForUpdate(id, businessId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
        ensureStaffOwns(sale);
        if (sale.getStatus() != SaleStatus.completed) {
            throw new ConflictException("Only completed sales can be paid");
        }
        BigDecimal amount = Money.parse(request.amount(), "amount");
        Money.requirePositive(amount, "amount");
        BigDecimal paid = salePaymentRepository.sumForSale(businessId, id);
        if (paid.add(amount).compareTo(sale.getTotalAmount()) > 0) {
            throw new ValidationException("amount", "would exceed the sale total");
        }
        Instant now = clock.instant();
        SalePayment payment = new SalePayment();
        payment.setId(Ids.newId());
        payment.setBusinessId(businessId);
        payment.setSaleId(id);
        payment.setAmount(amount);
        payment.setPaymentMethod(request.paymentMethod());
        payment.setReferenceNumber(trimToNull(request.referenceNumber()));
        payment.setPaidAt(now);
        payment.setCreatedAt(now);
        salePaymentRepository.save(payment);

        Map<String, Object> after = new HashMap<>();
        after.put("amount", Money.format(amount));
        after.put("paymentMethod", request.paymentMethod().name());
        auditService.record("sale.payment.add", "sale_payment", payment.getId(), null, after);
        return toPaymentResponse(payment);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public List<SalePaymentResponse> listPayments(UUID id) {
        UUID businessId = TenantContext.requireBusinessId();
        Sale sale = saleRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
        ensureStaffOwns(sale);
        return salePaymentRepository.findBySaleIdOrderByPaidAtAsc(id).stream()
                .map(this::toPaymentResponse).toList();
    }

    private Map<UUID, Product> lockProducts(UUID businessId, List<SaleItemRequest> lines) {
        Set<UUID> ids = lines.stream().map(SaleItemRequest::productId).collect(Collectors.toSet());
        Map<UUID, Product> products = new HashMap<>();
        List<UUID> ordered = ids.stream().sorted().toList();
        for (UUID productId : ordered) {
            Product product = productRepository.findForUpdate(productId, businessId)
                    .orElseThrow(() -> new NotFoundException("Product not found"));
            products.put(productId, product);
        }
        return products;
    }

    private Customer resolveCustomer(UUID customerId, UUID businessId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private BigDecimal resolveUnitPrice(String value, Product product) {
        BigDecimal price;
        if (value == null || value.isBlank()) {
            if (product.getSellingPrice() == null) {
                throw new ValidationException("unitPrice", "is required because the product has no selling price");
            }
            price = Money.of(product.getSellingPrice());
        } else {
            price = Money.parse(value, "unitPrice");
        }
        Money.requireNonNegative(price, "unitPrice");
        return price;
    }

    private SaleResponse toResponse(Sale sale, UUID businessId, boolean includeCost) {
        List<SaleItem> items = saleItemRepository.findBySaleIdOrderByCreatedAtAsc(sale.getId());
        Set<UUID> productIds = items.stream().map(SaleItem::getProductId).collect(Collectors.toSet());
        Map<UUID, String> productNames = productIds.isEmpty() ? Map.of()
                : productRepository.findByBusinessIdAndIdIn(businessId, productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Product::getName));
        Customer customer = sale.getCustomerId() == null ? null
                : customerRepository.findByIdAndBusinessId(sale.getCustomerId(), businessId).orElse(null);
        BigDecimal paid = salePaymentRepository.sumForSale(businessId, sale.getId());
        return toResponse(sale, items, customer == null ? null : customer.getName(), paid, productNames,
                includeCost);
    }

    private SaleResponse toResponse(Sale sale, List<SaleItem> items, String customerName, BigDecimal paid,
            Map<UUID, String> productNames, boolean includeCost) {
        List<SaleItemResponse> itemResponses = items.stream()
                .map(item -> new SaleItemResponse(item.getId(), item.getProductId(),
                        productNames.get(item.getProductId()), Quantity.format(item.getQuantity()),
                        Money.format(item.getUnitPrice()),
                        includeCost ? Money.format(item.getUnitCost()) : null,
                        Money.format(item.getSubtotal())))
                .toList();
        return new SaleResponse(sale.getId(), sale.getReferenceNumber(), sale.getCustomerId(), customerName,
                sale.getStatus().name(), Money.format(sale.getTotalAmount()), Money.format(paid),
                paymentStatus(paid, sale.getTotalAmount()), sale.getNotes(), sale.getSoldAt(),
                sale.getCreatedAt(), itemResponses);
    }

    private SaleSummaryResponse toSummary(Sale sale, String customerName, BigDecimal paid) {
        return new SaleSummaryResponse(sale.getId(), sale.getReferenceNumber(), sale.getCustomerId(), customerName,
                sale.getStatus().name(), Money.format(sale.getTotalAmount()), Money.format(paid),
                paymentStatus(paid, sale.getTotalAmount()), sale.getSoldAt(), sale.getCreatedAt());
    }

    private String paymentStatus(BigDecimal paid, BigDecimal total) {
        if (paid.signum() == 0) {
            return "unpaid";
        }
        return paid.compareTo(total) < 0 ? "partial" : "paid";
    }

    private SalePaymentResponse toPaymentResponse(SalePayment payment) {
        return new SalePaymentResponse(payment.getId(), Money.format(payment.getAmount()),
                payment.getPaymentMethod().name(), payment.getReferenceNumber(), payment.getPaidAt(),
                payment.getCreatedAt());
    }

    private void ensureStaffOwns(Sale sale) {
        if (isStaff() && !sale.getCreatedByUserId().equals(TenantContext.requireUserId())) {
            throw new NotFoundException("Sale not found");
        }
    }

    private boolean isStaff() {
        return "staff".equals(TenantContext.role());
    }

    private String nextSaleNumber(UUID businessId) {
        Long value = jdbcTemplate.queryForObject("""
                insert into document_counters (business_id, kind, last_value)
                values (?, 'sale', 1)
                on conflict (business_id, kind)
                do update set last_value = document_counters.last_value + 1
                returning last_value
                """, Long.class, businessId);
        return String.format(Locale.ROOT, "S-%06d", value == null ? 1L : value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
