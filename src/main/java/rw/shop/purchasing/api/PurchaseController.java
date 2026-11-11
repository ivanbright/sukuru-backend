package rw.shop.purchasing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.shop.common.pagination.PageResponse;
import rw.shop.purchasing.service.PurchaseService;

@RestController
@RequestMapping("/api/v1/purchases")
@Tag(name = "Purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    @Operation(summary = "List purchases")
    public PageResponse<PurchaseSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return purchaseService.list(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a purchase with items")
    public PurchaseResponse get(@PathVariable UUID id) {
        return purchaseService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft purchase")
    public PurchaseResponse create(@Valid @RequestBody PurchaseRequest request) {
        return purchaseService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a draft purchase")
    public PurchaseResponse update(@PathVariable UUID id, @Valid @RequestBody PurchaseRequest request) {
        return purchaseService.update(id, request);
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Receive a purchase and add stock")
    public PurchaseResponse receive(@PathVariable UUID id) {
        return purchaseService.receive(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a draft purchase")
    public PurchaseResponse cancel(@PathVariable UUID id) {
        return purchaseService.cancel(id);
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a purchase payment")
    public PurchasePaymentResponse addPayment(@PathVariable UUID id,
            @Valid @RequestBody PurchasePaymentRequest request) {
        return purchaseService.addPayment(id, request);
    }

    @GetMapping("/{id}/payments")
    @Operation(summary = "List purchase payments")
    public List<PurchasePaymentResponse> payments(@PathVariable UUID id) {
        return purchaseService.listPayments(id);
    }
}
