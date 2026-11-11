package rw.shop.common.cleanup;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final long retentionHours;

    public CleanupScheduler(JdbcTemplate jdbcTemplate, Clock clock,
            @Value("${app.idempotency.retention-hours:48}") long retentionHours) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.retentionHours = retentionHours;
    }

    @Scheduled(cron = "${app.cleanup.cron:0 0 3 * * *}")
    public void cleanup() {
        Instant now = clock.instant();
        Instant idempotencyBefore = now.minus(retentionHours, ChronoUnit.HOURS);
        jdbcTemplate.queryForObject("select fn_cleanup(?, ?)", (rs, rowNum) -> Boolean.TRUE,
                Timestamp.from(idempotencyBefore), Timestamp.from(now));
        log.info("Cleanup completed: idempotency keys before {} and refresh tokens before {}",
                idempotencyBefore, now);
    }
}
