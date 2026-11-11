package rw.shop.identity.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import rw.shop.common.error.RateLimitedException;

@Component
public class LoginRateLimiter {

    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> emailBuckets = new ConcurrentHashMap<>();
    private final int ipLimit;
    private final int emailLimit;

    public LoginRateLimiter(
            @Value("${app.rate-limit.login-per-ip-per-minute:10}") int ipLimit,
            @Value("${app.rate-limit.login-failures-per-email-per-15-minutes:5}") int emailLimit) {
        this.ipLimit = ipLimit;
        this.emailLimit = emailLimit;
    }

    public void checkIp(String ip) {
        String key = ip == null || ip.isBlank() ? "unknown" : ip;
        Bucket bucket = ipBuckets.computeIfAbsent(key, ignored -> newBucket(ipLimit, Duration.ofMinutes(1)));
        if (!bucket.tryConsume(1)) {
            throw new RateLimitedException("Too many login requests");
        }
    }

    public void checkEmail(String email) {
        Bucket bucket = emailBuckets.get(normalize(email));
        if (bucket != null && bucket.getAvailableTokens() <= 0) {
            throw new RateLimitedException("Too many failed login attempts");
        }
    }

    public void recordFailure(String email) {
        Bucket bucket = emailBuckets.computeIfAbsent(normalize(email),
                ignored -> newBucket(emailLimit, Duration.ofMinutes(15)));
        bucket.tryConsume(1);
    }

    public void reset(String email) {
        emailBuckets.remove(normalize(email));
    }

    private Bucket newBucket(int capacity, Duration period) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
