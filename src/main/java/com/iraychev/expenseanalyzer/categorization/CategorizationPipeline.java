package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.categorization.ai.AiCategorizer;
import com.iraychev.expenseanalyzer.categorization.ai.AiItem;
import com.iraychev.expenseanalyzer.categorization.ai.AiUnavailableException;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Categorizes cheapest first: per-user override, exact merchant cache, deterministic rules, and only then the AI,
 * batched per distinct merchant with bounded concurrency. Only AI answers are written back to the merchant cache.
 */
@Component
@Slf4j
public class CategorizationPipeline {

    /** Bump whenever rules, prompt or schema change so older results can be re-evaluated. */
    public static final int VERSION = 1;

    private final List<CategorizationLayer> layers;
    private final MerchantCacheLayer cache;
    private final AiCategorizer ai;
    private final int batchSize;
    private final ExecutorService aiExecutor;

    @Autowired
    public CategorizationPipeline(UserOverrideLayer overrides, MerchantCacheLayer cache, RuleLayer rules,
                                  AiCategorizer ai,
                                  @Value("${categorization.ai.batch-size:25}") int batchSize,
                                  @Value("${categorization.ai.concurrency:2}") int concurrency) {
        this.layers = List.of(overrides, cache, rules);
        this.cache = cache;
        this.ai = ai;
        this.batchSize = Math.max(1, batchSize);
        this.aiExecutor = concurrency > 1
                ? Executors.newFixedThreadPool(concurrency, runnable -> {
                    Thread thread = new Thread(runnable, "categorization-ai");
                    thread.setDaemon(true);
                    return thread;
                })
                : null;
    }

    public CategorizationPipeline(UserOverrideLayer overrides, MerchantCacheLayer cache, RuleLayer rules,
                                  AiCategorizer ai, int batchSize) {
        this(overrides, cache, rules, ai, batchSize, 1);
    }

    @PreDestroy
    void shutdown() {
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
    }

    /** @return one outcome per request, in the same order */
    public List<CategorizationOutcome> categorize(List<CategorizationRequest> requests) {
        CategorizationOutcome[] outcomes = new CategorizationOutcome[requests.size()];

        // Layers 1-3: each layer only sees what the previous ones could not decide.
        List<Integer> pending = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            pending.add(i);
        }
        for (CategorizationLayer layer : layers) {
            if (pending.isEmpty()) {
                break;
            }
            List<CategorizationRequest> subset = pending.stream().map(requests::get).toList();
            Map<Integer, Decision> decisions = layer.resolve(subset);
            List<Integer> stillPending = new ArrayList<>();
            for (int j = 0; j < pending.size(); j++) {
                Decision decision = decisions.get(j);
                if (decision != null) {
                    outcomes[pending.get(j)] = CategorizationOutcome.categorized(decision);
                } else {
                    stillPending.add(pending.get(j));
                }
            }
            pending = stillPending;
        }

        if (!pending.isEmpty()) {
            resolveWithAi(requests, pending, outcomes);
        }
        return List.of(outcomes);
    }

    private void resolveWithAi(List<CategorizationRequest> requests, List<Integer> pending,
                               CategorizationOutcome[] outcomes) {
        // One AI item per distinct merchant (or per distinct text when there is no merchant)
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        Map<String, AiItem> items = new LinkedHashMap<>();
        for (int index : pending) {
            ParsedTransaction parsed = requests.get(index).parsed();
            String groupKey = parsed.cacheKey() != null ? parsed.cacheKey() : "text:" + parsed.aiText();
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(index);
            items.computeIfAbsent(groupKey, k -> new AiItem(String.valueOf(items.size()), parsed.aiText(),
                    parsed.direction() == TransactionDirection.INCOME
                            ? TransactionDirection.INCOME : TransactionDirection.EXPENSE));
        }

        List<Map.Entry<String, AiItem>> entries = new ArrayList<>(items.entrySet());
        List<List<Map.Entry<String, AiItem>>> batches = new ArrayList<>();
        for (int from = 0; from < entries.size(); from += batchSize) {
            batches.add(entries.subList(from, Math.min(entries.size(), from + batchSize)));
        }

        // Start all batches (bounded by the pool size), then collect in order
        List<Future<Map<String, String>>> futures = new ArrayList<>();
        for (List<Map.Entry<String, AiItem>> batch : batches) {
            List<AiItem> aiItems = batch.stream().map(Map.Entry::getValue).toList();
            if (aiExecutor != null && batches.size() > 1) {
                futures.add(aiExecutor.submit(() -> ai.categorize(aiItems)));
            } else {
                futures.add(runNow(aiItems));
            }
        }

        for (int b = 0; b < batches.size(); b++) {
            List<Map.Entry<String, AiItem>> batch = batches.get(b);
            Map<String, String> answers;
            try {
                answers = futures.get(b).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                markUnavailable(batch, groups, outcomes, new AiUnavailableException("interrupted", 0, e));
                continue;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                markUnavailable(batch, groups, outcomes, cause instanceof AiUnavailableException unavailable
                        ? unavailable : new AiUnavailableException("AI call failed: " + cause, 0, cause));
                continue;
            }
            for (Map.Entry<String, AiItem> entry : batch) {
                if (!answers.containsKey(entry.getValue().id())) {
                    // The model skipped this item: a technical problem, not an "unknown" answer
                    groups.get(entry.getKey()).forEach(i -> outcomes[i] = CategorizationOutcome.failed());
                    continue;
                }
                Category category = toCategory(answers.get(entry.getValue().id()), entry.getValue().direction());
                CategorizationOutcome outcome = category == null
                        ? CategorizationOutcome.uncategorizable(CategorySource.AI)
                        : CategorizationOutcome.categorized(new Decision(category, CategorySource.AI));
                for (int i : groups.get(entry.getKey())) {
                    outcomes[i] = outcome;
                }
                if (category != null && !entry.getKey().startsWith("text:")) {
                    try {
                        cache.remember(entry.getKey(), category);
                    } catch (RuntimeException e) {
                        log.warn("Could not cache category for {}", entry.getKey(), e);
                    }
                }
            }
        }
    }

    private Future<Map<String, String>> runNow(List<AiItem> aiItems) {
        java.util.concurrent.CompletableFuture<Map<String, String>> future = new java.util.concurrent.CompletableFuture<>();
        try {
            future.complete(ai.categorize(aiItems));
        } catch (AiUnavailableException | RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private void markUnavailable(List<Map.Entry<String, AiItem>> batch, Map<String, List<Integer>> groups,
                                 CategorizationOutcome[] outcomes, AiUnavailableException e) {
        if (e.isCircuitOpen()) {
            log.info("AI circuit open, deferring {} merchant(s)", batch.size());
        } else {
            log.warn("AI categorization of {} merchant(s) failed: {}", batch.size(), e.getMessage());
        }
        CategorizationOutcome outcome = e.isCircuitOpen()
                ? CategorizationOutcome.deferred() : CategorizationOutcome.failed();
        for (Map.Entry<String, AiItem> entry : batch) {
            groups.get(entry.getKey()).forEach(i -> outcomes[i] = outcome);
        }
    }

    /** Validates an AI answer against the direction; null means "unknown". */
    private static Category toCategory(String answer, TransactionDirection direction) {
        if (answer == null || AiCategorizer.UNKNOWN.equals(answer)) {
            return null;
        }
        Category category = Category.fromKey(answer).orElse(null);
        if (category == null || category.kind() == Category.Kind.TRANSFER) {
            return null;
        }
        boolean fits = direction == TransactionDirection.INCOME
                ? category.kind() == Category.Kind.INCOME || category == Category.OTHER
                : category.kind() == Category.Kind.EXPENSE;
        return fits ? category : null;
    }
}
