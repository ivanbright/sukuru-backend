package rw.shop.billing.api;

public record BillingOverviewResponse(
        String plan,
        String planStatus,
        int workspaceCount,
        int workspaceLimit,
        String mobileMoneyNumber,
        String supportEmail,
        UpgradeRequestResponse latestRequest) {
}
