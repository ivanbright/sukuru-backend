package rw.shop.reporting.api;

public record SalesReportRow(
        String period,
        long salesCount,
        String revenue,
        String grossProfit) {
}
