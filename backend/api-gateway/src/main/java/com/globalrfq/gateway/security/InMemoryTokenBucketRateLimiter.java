package com.globalrfq.gateway.security;

import jakarta.validation.constraints.Min;
import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe, bounded-memory token-bucket RateLimiter for Spring Cloud Gateway.
 * Protects authentication and OTP endpoints without requiring an external Redis cluster.
 */
public class InMemoryTokenBucketRateLimiter extends AbstractRateLimiter<InMemoryTokenBucketRateLimiter.Config> {

    public static final String CONFIGURATION_PROPERTY_NAME = "in-memory-rate-limiter";
    private static final int MAX_TRACKED_KEYS = 50_000;
    private static final long STALE_BUCKET_NANOS = TimeUnit.MINUTES.toNanos(15);

    private final Config defaultConfig;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    InMemoryTokenBucketRateLimiter(int defaultReplenishRate, int defaultBurstCapacity) {
        this(null, defaultReplenishRate, defaultBurstCapacity);
    }

    public InMemoryTokenBucketRateLimiter(ConfigurationService configurationService,
                                          int defaultReplenishRate,
                                          int defaultBurstCapacity) {
        super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
        this.defaultConfig = new Config()
                .setReplenishRate(Math.max(1, defaultReplenishRate))
                .setBurstCapacity(Math.max(1, defaultBurstCapacity));
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        Config routeConfig = getConfig().getOrDefault(routeId, defaultConfig);
        int replenishRate = Math.max(1, routeConfig.getReplenishRate());
        int burstCapacity = Math.max(1, routeConfig.getBurstCapacity());

        if (buckets.size() > MAX_TRACKED_KEYS) {
            evictStaleBuckets(System.nanoTime());
        }

        String bucketKey = routeId + ":" + id;
        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(burstCapacity));
        BucketResult result = bucket.tryConsume(replenishRate, burstCapacity, System.nanoTime());

        Map<String, String> headers = Map.of(
                "X-RateLimit-Remaining", Long.toString(result.remainingTokens()),
                "X-RateLimit-Replenish-Rate", Integer.toString(replenishRate),
                "X-RateLimit-Burst-Capacity", Integer.toString(burstCapacity)
        );
        return Mono.just(new Response(result.allowed(), headers));
    }

    @Override
    public Config newConfig() {
        return new Config()
                .setReplenishRate(defaultConfig.getReplenishRate())
                .setBurstCapacity(defaultConfig.getBurstCapacity());
    }

    private void evictStaleBuckets(long nowNanos) {
        buckets.entrySet().removeIf(entry -> (nowNanos - entry.getValue().lastAccessNanos()) > STALE_BUCKET_NANOS);
        if (buckets.size() > MAX_TRACKED_KEYS) {
            buckets.clear();
        }
    }

    private record BucketResult(boolean allowed, long remainingTokens) { }

    private static final class TokenBucket {
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int initialCapacity) {
            this.tokens = initialCapacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized long lastAccessNanos() {
            return lastRefillNanos;
        }

        synchronized BucketResult tryConsume(int replenishRatePerSec, int burstCapacity, long nowNanos) {
            long elapsedNanos = Math.max(0L, nowNanos - lastRefillNanos);
            double added = (elapsedNanos / 1_000_000_000.0) * replenishRatePerSec;
            if (added > 0) {
                tokens = Math.min(burstCapacity, tokens + added);
                lastRefillNanos = nowNanos;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new BucketResult(true, (long) Math.floor(tokens));
            }
            return new BucketResult(false, 0L);
        }
    }

    @Validated
    public static class Config {
        @Min(1)
        private int replenishRate = 10;

        @Min(1)
        private int burstCapacity = 20;

        public int getReplenishRate() { return replenishRate; }
        public Config setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
            return this;
        }
        public int getBurstCapacity() { return burstCapacity; }
        public Config setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
            return this;
        }
    }
}
