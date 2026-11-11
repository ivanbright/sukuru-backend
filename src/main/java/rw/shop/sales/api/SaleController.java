package rw.shop.sales.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.shop.common.pagination.PageResponse;
import rw.shop.sales.service.SaleService;

@RestController
@RequestMapping("/api/v1/sales")
@Tag(name = "Sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    @Operation(summary = "List sales")
    public PageResponse<SaleSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return saleService.list(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a sale with items")
    public SaleResponse get(@PathVariable UUID id) {
        return saleService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a completed sale and decrement stock")
    public SaleResponse create(@Valid @RequestBody SaleRequest request) {
        return saleService.create(request);
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a sale payment")
    public SalePaymentResponse addPayment(@PathVariable UUID id, @Valid @RequestBody SalePaymentRequest request) {
        return saleService.addPayment(id, request);
    }

    @GetMapping("/{id}/payments")
    @Operation(summary = "List sale payments")
    public List<SalePaymentResponse> payments(@PathVariable UUID id) {
        return saleService.listPayments(id);
    }
}
