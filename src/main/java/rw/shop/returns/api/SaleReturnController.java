package rw.shop.returns.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import rw.shop.returns.service.SaleReturnService;

@RestController
@RequestMapping("/api/v1/returns")
@Tag(name = "Returns")
public class SaleReturnController {

    private final SaleReturnService returnService;

    public SaleReturnController(SaleReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping
    @Operation(summary = "List sale returns")
    public PageResponse<SaleReturnResponse> list(
            @RequestParam(required = false) UUID saleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return returnService.list(saleId, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a sale return")
    public SaleReturnResponse get(@PathVariable UUID id) {
        return returnService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a sale return", description = "Process customer return of previously sold items")
    public SaleReturnResponse create(@Valid @RequestBody SaleReturnRequest request) {
        return returnService.create(request);
    }
}
