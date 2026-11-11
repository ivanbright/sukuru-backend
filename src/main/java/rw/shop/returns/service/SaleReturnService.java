package rw.shop.returns.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import rw.shop.returns.api.ReturnItemRequest;
import rw.shop.returns.api.ReturnItemResponse;
import rw.shop.returns.api.SaleReturnRequest;
import rw.shop.returns.api.SaleReturnResponse;
import rw.shop.returns.domain.SaleReturn;
import rw.shop.returns.domain.SaleReturnItem;
import rw.shop.returns.repo.SaleReturnItemRepository;
import rw.shop.returns.repo.SaleReturnRepository;
import rw.shop.sales.domain.Sale;
import rw.shop.sales.domain.SaleItem;
import rw.shop.sales.domain.SaleStatus;
import rw.shop.sales.repo.SaleItemRepository;
import rw.shop.sales.repo.SaleRepository;

@Service
public class SaleReturnService {

    private final SaleReturnRepository returnRepository;
    private final SaleReturnItemRepository returnItemRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InventoryMovementRepository movementRepository;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SaleReturnService(SaleReturnRepository returnRepository, SaleReturnItemRepository returnItemRepository,
                             SaleRepository saleRepository, SaleItemRepository saleItemRepository,
                             ProductRepository productRepository, CustomerRepository customerRepository,
                             InventoryMovementRepository movementRepository, AuditService auditService,
                             JdbcTemplate jdbcTemplate, Clock clock) {
        this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository;
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.movementRepository = movementRepository;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public PageResponse<SaleReturnResponse> list(UUID saleId, int page, int size) {
        UUID businessId = TenantContext.requireBusinessId();
        Page<SaleReturn> returns = returnRepository.search(businessId, saleId,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<SaleReturnResponse> items = returns.getContent().stream()
                .map(ret -> toResponse(ret, businessId))
                .toList();
        return new PageResponse<>(items, returns.getNumber(), returns.getSize(), returns.getTotalElements());
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public SaleReturnResponse get(UUID id) {
        UUID businessId = TenantContext.requireBusinessId();
        SaleReturn ret = returnRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Return not found"));
        return toResponse(ret, businessId);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @Transactional
    public SaleReturnResponse create(SaleReturnRequest request) {
        UUID businessId = TenantContext.requireBusinessId();
        UUID userId = TenantContext.requireUserId();

        // Validate sale exists and is completed
        Sale sale = saleRepository.findByIdAndBusinessId(request.saleId(), businessId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
        if (sale.getStatus() != SaleStatus.completed) {
            throw new ValidationException("Can only return items from completed sales");
        }

        // Load sale items
        List<SaleItem> saleItems = saleItemRepository.findBySaleIdOrderByCreatedAtAsc(request.saleId());
        Map<UUID, SaleItem> saleItemMap = saleItems.stream()
                .collect(Collectors.toMap(SaleItem::getId, Function.identity()));

        // Validate return items
        for (ReturnItemRequest item : request.items()) {
            SaleItem saleItem = saleItemMap.get(item.saleItemId());
            if (saleItem == null) {
                throw new ValidationException("Sale item " + item.saleItemId() + " not found in this sale");
            }
            if (!saleItem.getProductId().equals(item.productId())) {
                throw new ValidationException("Product ID mismatch for sale item " + item.saleItemId());
            }
            if (item.quantityReturnedValue().compareTo(saleItem.getQuantity()) > 0) {
                throw new ValidationException("Cannot return more than sold quantity for item " + item.saleItemId());
            }
        }

        // Calculate total
        BigDecimal total = request.items().stream()
                .map(item -> {
                    SaleItem saleItem = saleItemMap.get(item.saleItemId());
                    return saleItem.getUnitPrice().multiply(item.quantityReturnedValue());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Generate reference number
        String referenceNumber = generateReferenceNumber(businessId);

        // Create return
        SaleReturn ret = new SaleReturn();
        ret.setId(Ids.newId());
        ret.setBusinessId(businessId);
        ret.setSaleId(sale.getId());
        ret.setCustomerId(sale.getCustomerId());
        ret.setCreatedByUserId(userId);
        ret.setReferenceNumber(referenceNumber);
        ret.setTotalAmount(total);
        ret.setRefundMethod(request.refundMethod());
        ret.setNotes(request.notes());
        ret.setReturnedAt(clock.instant());
        ret.setCreatedAt(clock.instant());
        returnRepository.save(ret);

        // Create return items and inventory movements
        List<SaleReturnItem> returnItems = new ArrayList<>();
        List<InventoryMovement> movements = new ArrayList<>();

        for (ReturnItemRequest itemReq : request.items()) {
            SaleItem saleItem = saleItemMap.get(itemReq.saleItemId());
            BigDecimal subtotal = saleItem.getUnitPrice().multiply(itemReq.quantityReturnedValue());

            SaleReturnItem returnItem = new SaleReturnItem();
            returnItem.setId(Ids.newId());
            returnItem.setBusinessId(businessId);
            returnItem.setSaleReturnId(ret.getId());
            returnItem.setSaleItemId(saleItem.getId());
            returnItem.setProductId(saleItem.getProductId());
            returnItem.setQuantityReturned(itemReq.quantityReturnedValue());
            returnItem.setUnitPrice(saleItem.getUnitPrice());
            returnItem.setSubtotal(subtotal);
            returnItem.setCreatedAt(clock.instant());
            returnItems.add(returnItem);

            // Create inventory movement (return adds stock back)
            InventoryMovement movement = new InventoryMovement();
            movement.setId(Ids.newId());
            movement.setBusinessId(businessId);
            movement.setProductId(saleItem.getProductId());
            movement.setMovementType(MovementType.returning);
            movement.setQuantityDelta(itemReq.quantityReturnedValue()); // Positive for returns
            movement.setSaleReturnItemId(returnItem.getId());
            movement.setReason("Return from sale " + sale.getReferenceNumber());
            movement.setCreatedByUserId(userId);
            movement.setCreatedAt(clock.instant());
            movements.add(movement);
        }

        returnItemRepository.saveAll(returnItems);
        movementRepository.saveAll(movements);

        // Audit log
        auditService.record("sale_return.create", "sale_return", ret.getId(), null, ret);

        return toResponse(ret, businessId);
    }

    private String generateReferenceNumber(UUID businessId) {
        Long value = jdbcTemplate.queryForObject("""
                insert into document_counters (business_id, kind, last_value)
                values (?, 'return', 1)
                on conflict (business_id, kind)
                do update set last_value = document_counters.last_value + 1
                returning last_value
                """, Long.class, businessId);
        return String.format(java.util.Locale.ROOT, "R-%06d", value == null ? 1L : value);
    }

    private SaleReturnResponse toResponse(SaleReturn ret, UUID businessId) {
        // Load related data
        Sale sale = saleRepository.findById(ret.getSaleId()).orElse(null);
        String saleReference = sale != null ? sale.getReferenceNumber() : null;

        Customer customer = ret.getCustomerId() != null
                ? customerRepository.findById(ret.getCustomerId()).orElse(null)
                : null;
        String customerName = customer != null ? customer.getName() : null;

        List<SaleReturnItem> items = returnItemRepository.findBySaleReturnId(ret.getId(), businessId);
        List<UUID> productIds = items.stream().map(SaleReturnItem::getProductId).distinct().toList();
        Map<UUID, String> productNames = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));

        List<ReturnItemResponse> itemResponses = items.stream()
                .map(item -> new ReturnItemResponse(
                        item.getId(),
                        item.getSaleItemId(),
                        item.getProductId(),
                        productNames.get(item.getProductId()),
                        item.getQuantityReturned(),
                        item.getUnitPrice(),
                        item.getSubtotal()))
                .toList();

        return new SaleReturnResponse(
                ret.getId(),
                ret.getSaleId(),
                saleReference,
                ret.getCustomerId(),
                customerName,
                ret.getReferenceNumber(),
                ret.getTotalAmount(),
                ret.getRefundMethod(),
                ret.getNotes(),
                itemResponses,
                ret.getReturnedAt(),
                ret.getCreatedAt());
    }
}
