package rw.shop.reporting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.shop.reporting.service.ReportService;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Dashboard summary for a date range")
    public ReportSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.summary(from, to);
    }

    @GetMapping("/sales")
    @Operation(summary = "Sales totals grouped by day, week, or month")
    public List<SalesReportRow> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "day") String groupBy) {
        return reportService.sales(from, to, groupBy);
    }

    @GetMapping("/top-products")
    @Operation(summary = "Best selling products for a date range")
    public List<TopProductResponse> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.topProducts(from, to, limit);
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Active products at or below a stock threshold")
    public List<LowStockResponse> lowStock(@RequestParam(defaultValue = "5") int threshold) {
        return reportService.lowStock(threshold);
    }
}
