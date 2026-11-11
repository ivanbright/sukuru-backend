package rw.shop.billing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.shop.billing.service.BillingService;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing")
public class BillingController {

    private final BillingService billingService;
    private final String mobileMoneyNumber;
    private final String supportEmail;

    public BillingController(BillingService billingService,
            @Value("${app.billing.mobile-money-number}") String mobileMoneyNumber,
            @Value("${app.billing.support-email}") String supportEmail) {
        this.billingService = billingService;
        this.mobileMoneyNumber = mobileMoneyNumber;
        this.supportEmail = supportEmail;
    }

    @GetMapping
    @Operation(summary = "Get the current plan and workspace allowance")
    public BillingOverviewResponse overview() {
        return billingService.overview(mobileMoneyNumber, supportEmail);
    }

    @PostMapping("/upgrade-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Request a Premium plan upgrade")
    public UpgradeRequestResponse requestUpgrade(@Valid @RequestBody UpgradeRequest request) {
        return billingService.requestUpgrade(request);
    }

    @PostMapping("/admin/requests/{requestId}/approve")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Approve a manually verified Premium payment")
    public UpgradeRequestResponse approve(@PathVariable UUID requestId,
            @RequestHeader("X-Billing-Admin-Key") String adminKey,
            @Valid @RequestBody ApproveUpgradeRequest request) {
        return billingService.approve(requestId, request.userId(), adminKey);
    }
}
