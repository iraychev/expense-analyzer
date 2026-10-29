package com.iraychev.expenseanalyzer.categorization.ai;

import com.iraychev.expenseanalyzer.categorization.TransactionDirection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResilientAiCategorizerTest {

    private static final List<AiItem> ITEMS = List.of(new AiItem("0", "x", TransactionDirection.EXPENSE));

    private final AtomicInteger calls = new AtomicInteger();
    private final List<Long> sleeps = new ArrayList<>();
    private final AtomicLong now = new AtomicLong(1_000);

    private ResilientAiCategorizer resilient(AiCategorizer delegate, int attempts, int threshold) {
        return new ResilientAiCategorizer(delegate, attempts, 100, threshold, 60_000, sleeps::add, now::get);
    }

    private AiCategorizer failing(int status) {
        return items -> {
            calls.incrementAndGet();
            throw new AiUnavailableException("fail", status);
        };
    }

    @Test
    void retriesTransientFailuresWithExponentialBackoffThenSucceeds() throws Exception {
        AiCategorizer flaky = items -> {
            if (calls.incrementAndGet() < 3) {
                throw new AiUnavailableException("rate limited", 429);
            }
            return Map.of("0", "Shopping");
        };

        Map<String, String> result = resilient(flaky, 3, 5).categorize(ITEMS);

        assertEquals("Shopping", result.get("0"));
        assertEquals(3, calls.get());
        assertEquals(2, sleeps.size());
        assertTrue(sleeps.get(0) >= 100 && sleeps.get(0) < 200, "first backoff 100 ms + jitter: " + sleeps.get(0));
        assertTrue(sleeps.get(1) >= 200 && sleeps.get(1) < 300, "second backoff doubles: " + sleeps.get(1));
    }

    @Test
    void doesNotRetryClientErrorsLikeABadApiKey() {
        assertThrows(AiUnavailableException.class, () -> resilient(failing(403), 3, 5).categorize(ITEMS));
        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void givesUpAfterMaxAttemptsAndRethrowsTheLastError() {
        AiUnavailableException e = assertThrows(AiUnavailableException.class,
                () -> resilient(failing(503), 3, 5).categorize(ITEMS));
        assertEquals(503, e.getStatusCode());
        assertEquals(3, calls.get());
    }

    @Test
    void circuitOpensAfterRepeatedFailuresFailsFastAndClosesAfterTheCooldown() throws Exception {
        AtomicInteger delegateCalls = new AtomicInteger();
        boolean[] healthy = {false};
        AiCategorizer delegate = items -> {
            delegateCalls.incrementAndGet();
            if (!healthy[0]) {
                throw new AiUnavailableException("down", 500);
            }
            return Map.of("0", "Fees");
        };
        ResilientAiCategorizer ai = resilient(delegate, 1, 2);

        assertThrows(AiUnavailableException.class, () -> ai.categorize(ITEMS));
        assertFalse(ai.isCircuitOpen());
        assertThrows(AiUnavailableException.class, () -> ai.categorize(ITEMS));
        assertTrue(ai.isCircuitOpen());

        int before = delegateCalls.get();
        AiUnavailableException rejected = assertThrows(AiUnavailableException.class, () -> ai.categorize(ITEMS));
        assertTrue(rejected.isCircuitOpen());
        assertEquals(before, delegateCalls.get(), "no call is made while the circuit is open");

        now.addAndGet(61_000);
        healthy[0] = true;
        assertEquals("Fees", ai.categorize(ITEMS).get("0"));
        assertFalse(ai.isCircuitOpen());
    }

    @Test
    void aSuccessResetsTheFailureCount() throws Exception {
        boolean[] fail = {true};
        AiCategorizer delegate = items -> {
            if (fail[0]) {
                throw new AiUnavailableException("down", 500);
            }
            return Map.of();
        };
        ResilientAiCategorizer ai = resilient(delegate, 1, 2);

        assertThrows(AiUnavailableException.class, () -> ai.categorize(ITEMS));
        fail[0] = false;
        ai.categorize(ITEMS);
        fail[0] = true;
        assertThrows(AiUnavailableException.class, () -> ai.categorize(ITEMS));

        assertFalse(ai.isCircuitOpen(), "failures must be consecutive to open the circuit");
    }

    @Test
    void springBootConvertsTheDurationFormatsUsedInTheConfiguration() {
        assertEquals(Duration.ofHours(6), ApplicationConversionService.getSharedInstance().convert("6h", Duration.class));
        assertEquals(Duration.ofMinutes(5), ApplicationConversionService.getSharedInstance().convert("PT5M", Duration.class));
    }
}
