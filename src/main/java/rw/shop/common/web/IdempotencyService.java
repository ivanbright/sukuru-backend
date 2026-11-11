package rw.shop.common.web;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import rw.shop.common.Ids;
import rw.shop.common.error.ConflictException;
import rw.shop.common.error.IdempotencyKeyReusedException;

@Service
public class IdempotencyService {

    public record StoredResponse(int statusCode, String body) {
    }

    private record StoredRow(String requestHash, Integer statusCode, String body) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Optional<StoredResponse> begin(UUID businessId, String key, String requestHash) {
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                    insert into idempotency_keys (id, business_id, key, request_hash, created_at)
                    values (?, ?, ?, ?, ?)
                    """, Ids.newId(), businessId, key, requestHash, Timestamp.from(clock.instant())));
            return Optional.empty();
        } catch (DuplicateKeyException ex) {
            StoredRow row = read(businessId, key);
            if (row == null) {
                throw new ConflictException("Request is still being processed");
            }
            if (!row.requestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }
            if (row.statusCode() != null) {
                return Optional.of(new StoredResponse(row.statusCode(), row.body()));
            }
            return Optional.of(waitForCompletion(businessId, key, requestHash));
        }
    }

    public void complete(UUID businessId, String key, int statusCode, String body) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                update idempotency_keys
                set status_code = ?, response_body = cast(? as jsonb)
                where business_id = ? and key = ?
                """, statusCode, body, businessId, key));
    }

    private StoredResponse waitForCompletion(UUID businessId, String key, String requestHash) {
        Instant deadline = clock.instant().plusSeconds(30);
        while (clock.instant().isBefore(deadline)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ConflictException("Request is still being processed");
            }
            StoredRow row = read(businessId, key);
            if (row != null && row.statusCode() != null) {
                if (!row.requestHash().equals(requestHash)) {
                    throw new IdempotencyKeyReusedException();
                }
                return new StoredResponse(row.statusCode(), row.body());
            }
        }
        throw new ConflictException("Request is still being processed");
    }

    private StoredRow read(UUID businessId, String key) {
        return transactionTemplate.execute(status -> {
            var rows = jdbcTemplate.query("""
                    select request_hash, status_code, response_body::text
                    from idempotency_keys
                    where business_id = ? and key = ?
                    """, (rs, rowNum) -> new StoredRow(
                    rs.getString("request_hash"),
                    rs.getObject("status_code", Integer.class),
                    rs.getString(3)), businessId, key);
            return rows.isEmpty() ? null : rows.get(0);
        });
    }
}
