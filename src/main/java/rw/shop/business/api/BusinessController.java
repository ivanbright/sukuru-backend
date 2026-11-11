package rw.shop.business.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.shop.business.service.BusinessService;

@RestController
@RequestMapping("/api/v1/business")
@Tag(name = "Business")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping
    @Operation(summary = "Get current business details")
    public BusinessResponse get() {
        return businessService.get();
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Create another business for the signed-in owner")
    public BusinessResponse create(@Valid @RequestBody BusinessRequest request) {
        return businessService.createForCurrentOwner(request);
    }

    @PutMapping
    @Operation(summary = "Update business details (Owner only)")
    public BusinessResponse update(@Valid @RequestBody BusinessRequest request) {
        return businessService.update(request);
    }
}
