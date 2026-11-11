package rw.shop.inventory.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.shop.common.pagination.PageResponse;
import rw.shop.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stock")
    @Operation(summary = "List current stock levels")
    public PageResponse<StockResponse> stock(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.listStock(search, page, size);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a stock adjustment")
    public MovementResponse adjust(@Valid @RequestBody AdjustmentRequest request) {
        return inventoryService.adjust(request);
    }

    @GetMapping("/movements")
    @Operation(summary = "List inventory movements")
    public PageResponse<MovementResponse> movements(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.listMovements(productId, type, from, to, page, size);
    }
}
