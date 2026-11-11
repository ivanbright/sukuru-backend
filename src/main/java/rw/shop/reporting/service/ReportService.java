package rw.shop.reporting.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.shop.common.Money;
import rw.shop.common.Quantity;
import rw.shop.common.error.ValidationException;
import rw.shop.common.web.TenantContext;
import rw.shop.reporting.api.LowStockResponse;
import rw.shop.reporting.api.ReportSummaryResponse;
import rw.shop.reporting.api.SalesReportRow;
import rw.shop.reporting.api.TopProductResponse;

@Service
public class ReportService {

    private record Totals(long count, BigDecimal amount) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional(readOnly = true)
    public ReportSummaryResponse summary(Instant fromParam, Instant toParam) {
        Instant to = toParam == null ? clock.instant() : toParam;
        Instant from = fromParam == null ? to.minus(30, ChronoUnit.DAYS) : fromParam;
        validateRange(from, to);
        var businessId = TenantContext.requireBusinessId();

        Totals sales = jdbcTemplate.queryForObject("""
                select count(*) as cnt, coalesce(sum(total_amount), 0) as amount
                from sales
                where business_id = ? and status = 'completed' and sold_at >= ? and sold_at < ?
                """, (rs, rowNum) -> new Totals(rs.getLong("cnt"), rs.getBigDecimal("amount")),
                businessId, Timestamp.from(from), Timestamp.from(to));
        Totals purchases = jdbcTemplate.queryForObject("""
                select count(*) as cnt, coalesce(sum(total_amount), 0) as amount
                from purchases
                where business_id = ? and status = 'received' and purchased_at >= ? and purchased_at < ?
                """, (rs, rowNum) -> new Totals(rs.getLong("cnt"), rs.getBigDecimal("amount")),
                businessId, Timestamp.from(from), Timestamp.from(to));
        BigDecimal cogs = jdbcTemplate.queryForObject("""
                select coalesce(sum(si.quantity * si.unit_cost), 0)
                from sale_items si
                join sales s on s.id = si.sale_id
                where s.business_id = ? and s.status = 'completed' and s.sold_at >= ? and s.sold_at < ?
                """, BigDecimal.class, businessId, Timestamp.from(from), Timestamp.from(to));

        BigDecimal revenue = sales == null ? BigDecimal.ZERO : sales.amount();
        BigDecimal cost = cogs == null ? BigDecimal.ZERO : cogs;
        return new ReportSummaryResponse(
                sales == null ? 0 : sales.count(),
                Money.format(revenue),
                purchases == null ? 0 : purchases.count(),
                Money.format(purchases == null ? BigDecimal.ZERO : purchases.amount()),
                Money.format(revenue.subtract(cost)));
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional(readOnly = true)
    public List<SalesReportRow> sales(Instant fromParam, Instant toParam, String groupBy) {
        Instant to = toParam == null ? clock.instant() : toParam;
        Instant from = fromParam == null ? to.minus(30, ChronoUnit.DAYS) : fromParam;
        validateRange(from, to);
        String unit = switch (groupBy == null ? "day" : groupBy.toLowerCase(Locale.ROOT)) {
            case "day" -> "day";
            case "week" -> "week";
            case "month" -> "month";
            default -> throw new ValidationException("groupBy", "must be one of day, week, month");
        };
        var businessId = TenantContext.requireBusinessId();
        String sql = """
                select date_trunc('%s', s.sold_at) as bucket,
                       count(distinct s.id) as sales_count,
                       coalesce(sum(si.subtotal), 0) as revenue,
                       coalesce(sum(si.subtotal - si.quantity * si.unit_cost), 0) as profit
                from sales s
                join sale_items si on si.sale_id = s.id
                where s.business_id = ? and s.status = 'completed' and s.sold_at >= ? and s.sold_at < ?
                group by bucket
                order by bucket
                """.formatted(unit);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Instant bucket = rs.getTimestamp("bucket").toInstant();
            return new SalesReportRow(period(bucket, unit), rs.getLong("sales_count"),
                    Money.format(rs.getBigDecimal("revenue")), Money.format(rs.getBigDecimal("profit")));
        }, businessId, Timestamp.from(from), Timestamp.from(to));
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional(readOnly = true)
    public List<TopProductResponse> topProducts(Instant fromParam, Instant toParam, int limit) {
        Instant to = toParam == null ? clock.instant() : toParam;
        Instant from = fromParam == null ? to.minus(30, ChronoUnit.DAYS) : fromParam;
        validateRange(from, to);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        var businessId = TenantContext.requireBusinessId();
        return jdbcTemplate.query("""
                select si.product_id, p.name,
                       sum(si.quantity) as quantity,
                       coalesce(sum(si.subtotal), 0) as revenue,
                       coalesce(sum(si.subtotal - si.quantity * si.unit_cost), 0) as profit
                from sale_items si
                join sales s on s.id = si.sale_id
                join products p on p.id = si.product_id
                where s.business_id = ? and s.status = 'completed' and s.sold_at >= ? and s.sold_at < ?
                group by si.product_id, p.name
                order by quantity desc, revenue desc
                limit ?
                """, (rs, rowNum) -> new TopProductResponse(
                        rs.getObject("product_id", java.util.UUID.class),
                        rs.getString("name"),
                        Quantity.format(rs.getBigDecimal("quantity")),
                        Money.format(rs.getBigDecimal("revenue")),
                        Money.format(rs.getBigDecimal("profit"))),
                businessId, Timestamp.from(from), Timestamp.from(to), safeLimit);
    }

    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional(readOnly = true)
    public List<LowStockResponse> lowStock(int threshold) {
        if (threshold < 0) {
            throw new ValidationException("threshold", "must be at or above 0");
        }
        var businessId = TenantContext.requireBusinessId();
        return jdbcTemplate.query("""
                select p.id, p.name, p.sku, p.unit, coalesce(sum(m.quantity_delta), 0) as quantity
                from products p
                left join inventory_movements m
                       on m.product_id = p.id and m.business_id = p.business_id
                where p.business_id = ? and p.is_active = true
                group by p.id, p.name, p.sku, p.unit
                having coalesce(sum(m.quantity_delta), 0) <= ?
                order by quantity asc, p.name asc
                """, (rs, rowNum) -> new LowStockResponse(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getString("name"),
                        rs.getString("sku"),
                        rs.getString("unit"),
                        Quantity.format(rs.getBigDecimal("quantity"))),
                businessId, threshold);
    }

    private void validateRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ValidationException("from", "must be before to");
        }
    }

    private String period(Instant bucket, String unit) {
        ZonedDateTime value = bucket.atZone(ZoneOffset.UTC);
        return switch (unit) {
            case "week" -> String.format(Locale.ROOT, "%d-W%02d",
                    value.get(IsoFields.WEEK_BASED_YEAR), value.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case "month" -> value.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            default -> value.toLocalDate().toString();
        };
    }
}
