package com.commitgen.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class RateLimitService {

    // Buckets por IP com TTL de 2h — entradas expiram automaticamente, sem memory leak
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(10_000)
            .build();

    public static final int MAX_REQUESTS_PER_HOUR = 10;
    public static final Duration REFILL_PERIOD = Duration.ofHours(1);

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(MAX_REQUESTS_PER_HOUR)
                        .refillGreedy(MAX_REQUESTS_PER_HOUR, REFILL_PERIOD)
                        .build())
                .build();
    }

    /**
     * Tenta consumir 1 token e retorna informações detalhadas sobre o rate limit.
     */
    public RateLimitInfo consume(String ip) {
        Bucket bucket = buckets.get(ip, k -> createBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        long remaining = probe.getRemainingTokens();
        long nanosToRefill = probe.getNanosToWaitForRefill();

        long resetEpochSeconds;
        if (remaining >= MAX_REQUESTS_PER_HOUR - 1) {
            // bucket cheio (ou quase) — reset daqui a 1h
            resetEpochSeconds = Instant.now().plus(REFILL_PERIOD).getEpochSecond();
        } else {
            // estima quando TODOS os tokens estarão de volta
            long tokensUsed = MAX_REQUESTS_PER_HOUR - remaining;
            long nanosPerToken = REFILL_PERIOD.toNanos() / MAX_REQUESTS_PER_HOUR;
            long nanosToFull = tokensUsed * nanosPerToken;
            resetEpochSeconds = Instant.now().plusNanos(nanosToFull).getEpochSecond();
        }

        return new RateLimitInfo(
                probe.isConsumed(),
                remaining,
                MAX_REQUESTS_PER_HOUR,
                resetEpochSeconds,
                nanosToRefill > 0 ? (long) Math.ceil(nanosToRefill / 1_000_000_000.0) : 0
        );
    }

    /**
     * Retorna informações de rate limit SEM consumir tokens (para consulta).
     */
    public RateLimitInfo getInfo(String ip) {
        Bucket bucket = buckets.get(ip, k -> createBucket());
        long available = bucket.getAvailableTokens();

        long resetEpochSeconds;
        if (available >= MAX_REQUESTS_PER_HOUR) {
            resetEpochSeconds = Instant.now().plus(REFILL_PERIOD).getEpochSecond();
        } else {
            long tokensUsed = MAX_REQUESTS_PER_HOUR - available;
            long nanosPerToken = REFILL_PERIOD.toNanos() / MAX_REQUESTS_PER_HOUR;
            long nanosToFull = tokensUsed * nanosPerToken;
            resetEpochSeconds = Instant.now().plusNanos(nanosToFull).getEpochSecond();
        }

        return new RateLimitInfo(
                available > 0,
                available,
                MAX_REQUESTS_PER_HOUR,
                resetEpochSeconds,
                available > 0 ? 0 : REFILL_PERIOD.toSeconds() / MAX_REQUESTS_PER_HOUR
        );
    }

    /**
     * Tenta consumir 1 token do bucket do IP informado.
     * @return true se ainda há tokens disponíveis (requisição permitida)
     */
    public boolean tryConsume(String ip) {
        return buckets.get(ip, k -> createBucket()).tryConsume(1);
    }

    /**
     * Retorna quantos tokens ainda restam para o IP.
     */
    public long getAvailableTokens(String ip) {
        return buckets.get(ip, k -> createBucket()).getAvailableTokens();
    }

    /**
     * Informações detalhadas sobre o rate limit de um IP.
     */
    @Getter
    public static class RateLimitInfo {
        private final boolean allowed;
        private final long remaining;
        private final long limit;
        private final long resetAtEpochSeconds;
        private final long retryAfterSeconds;

        public RateLimitInfo(boolean allowed, long remaining, long limit,
                             long resetAtEpochSeconds, long retryAfterSeconds) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.limit = limit;
            this.resetAtEpochSeconds = resetAtEpochSeconds;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }
}
