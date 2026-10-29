package com.iraychev.expenseanalyzer.categorization.ai;

import com.iraychev.expenseanalyzer.service.AiCategorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Retries transient AI failures (429, 5xx, network) with exponential backoff and jitter, and opens a circuit
 * breaker after several calls in a row that failed even after their retries: while open, calls fail fast with
 * {@link AiUnavailableException#circuitOpen()} so the worker can park rows instead of hammering a broken API.
 */
@Component
@Primary
@Slf4j
public class ResilientAiCategorizer implements AiCategorizer {

    /** Sleeps; replaced in tests. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final AiCategorizer delegate;
    private final int maxAttempts;
    private final long baseDelayMillis;
    private final int failureThreshold;
    private final long cooldownMillis;
    private final Sleeper sleeper;
    private final LongSupplier clock;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long openUntil = 0;

    @Autowired
    public ResilientAiCategorizer(AiCategorizationService delegate,
                                  @Value("${categorization.ai.max-attempts:3}") int maxAttempts,
                                  @Value("${categorization.ai.retry-base-delay:PT2S}") Duration baseDelay,
                                  @Value("${categorization.ai.circuit-failure-threshold:5}") int failureThreshold,
                                  @Value("${categorization.ai.circuit-cooldown:PT5M}") Duration cooldown) {
        this(delegate, maxAttempts, baseDelay.toMillis(), failureThreshold, cooldown.toMillis(),
                Thread::sleep, System::currentTimeMillis);
    }

    public ResilientAiCategorizer(AiCategorizer delegate, int maxAttempts, long baseDelayMillis, int failureThreshold,
                                  long cooldownMillis, Sleeper sleeper, LongSupplier clock) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseDelayMillis = Math.max(0, baseDelayMillis);
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownMillis = cooldownMillis;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    public boolean isCircuitOpen() {
        return clock.getAsLong() < openUntil;
    }

    @Override
    public Map<String, String> categorize(List<AiItem> items) throws AiUnavailableException {
        if (isCircuitOpen()) {
            throw AiUnavailableException.circuitOpen();
        }
        AiUnavailableException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Map<String, String> result = delegate.categorize(items);
                consecutiveFailures.set(0);
                return result;
            } catch (AiUnavailableException e) {
                last = e;
                if (!e.isTransient() || attempt == maxAttempts) {
                    break;
                }
                long delay = baseDelayMillis * (1L << (attempt - 1))
                        + (baseDelayMillis > 0 ? ThreadLocalRandom.current().nextLong(baseDelayMillis) : 0);
                log.info("AI call failed ({}), retry {}/{} in {} ms", e.getMessage(), attempt, maxAttempts - 1, delay);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AiUnavailableException("interrupted while backing off", 0, ie);
                }
            }
        }
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntil = clock.getAsLong() + cooldownMillis;
            consecutiveFailures.set(0);
            log.error("AI categorization failed {} calls in a row, pausing it for {} ms", failureThreshold, cooldownMillis);
        }
        throw last;
    }
}
