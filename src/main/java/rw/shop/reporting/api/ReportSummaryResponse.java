package rw.shop.reporting.api;

public record ReportSummaryResponse(
        long salesCount,
        String salesRevenue,
        long purchasesCount,
        String purchaseCost,
        String grossProfit) {
}
