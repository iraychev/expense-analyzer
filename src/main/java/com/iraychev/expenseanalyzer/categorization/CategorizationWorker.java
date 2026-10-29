package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.categorization.parser.TransactionParsers;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Categorizes PENDING transactions outside the request path.
 * <ol>
 *   <li>claim: in a short transaction lock due rows with {@code FOR UPDATE SKIP LOCKED} and stamp {@code claimedAt}
 *       (a lease, so rows of a crashed worker become due again);</li>
 *   <li>resolve: parse, then run the {@link CategorizationPipeline} (cache, rules, batched AI) without any
 *       database transaction open;</li>
 *   <li>persist: in a short transaction write the outcomes to the rows that are still PENDING.</li>
 * </ol>
 * It runs on a schedule and is triggered right after a sync. A transient failure keeps the row PENDING with an
 * exponentially growing {@code nextRetryAt}; after {@code max-retries} failures the row becomes FAILED.
 */
@Component
@Slf4j
public class CategorizationWorker {

    /** What one run did. */
    public record RunStats(int claimed, int categorized, int uncategorizable, int retryLater, int failed) {
        static RunStats empty() {
            return new RunStats(0, 0, 0, 0, 0);
        }

        RunStats plus(RunStats other) {
            return new RunStats(claimed + other.claimed, categorized + other.categorized,
                    uncategorizable + other.uncategorizable, retryLater + other.retryLater, failed + other.failed);
        }
    }

    private record Claimed(long id, Long userId, String institutionId, List<String> holderNames, BigDecimal amount,
                           String description, String counterparty, String typeCode) {
    }

    static final Duration FAILED_RETRY_BASE = Duration.ofDays(1);
    static final Duration FAILED_RETRY_CAP = Duration.ofDays(7);
    static final Duration UNCATEGORIZABLE_RETRY_AFTER = Duration.ofDays(30);

    private final TransactionRepository repository;
    private final TransactionParsers parsers;
    private final CategorizationPipeline pipeline;
    private final TransactionTemplate tx;

    private final int claimSize;
    private final int maxBatchesPerRun;
    private final int maxRetries;
    private final Duration retryBaseDelay;
    private final Duration lease;
    private final Duration deferDelay;

    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService trigger = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "categorization-trigger");
        thread.setDaemon(true);
        return thread;
    });

    public CategorizationWorker(TransactionRepository repository, TransactionParsers parsers,
                                CategorizationPipeline pipeline, PlatformTransactionManager transactionManager,
                                @Value("${categorization.worker.claim-size:100}") int claimSize,
                                @Value("${categorization.worker.max-batches-per-run:50}") int maxBatchesPerRun,
                                @Value("${categorization.worker.max-retries:5}") int maxRetries,
                                @Value("${categorization.worker.retry-base-delay:PT5M}") Duration retryBaseDelay,
                                @Value("${categorization.worker.lease:PT10M}") Duration lease,
                                @Value("${categorization.ai.circuit-cooldown:PT5M}") Duration deferDelay) {
        this.repository = repository;
        this.parsers = parsers;
        this.pipeline = pipeline;
        this.tx = new TransactionTemplate(transactionManager);
        this.claimSize = Math.max(1, claimSize);
        this.maxBatchesPerRun = Math.max(1, maxBatchesPerRun);
        this.maxRetries = Math.max(1, maxRetries);
        this.retryBaseDelay = retryBaseDelay;
        this.lease = lease;
        this.deferDelay = deferDelay;
    }

    @PreDestroy
    void shutdown() {
        trigger.shutdownNow();
    }

    @Scheduled(fixedDelayString = "${categorization.worker.interval:PT1M}",
            initialDelayString = "${categorization.worker.initial-delay:PT30S}")
    public void scheduledRun() {
        runNow();
    }

    /** Asks for a run without waiting for it (used right after a sync). Never throws. */
    public void triggerAsync() {
        try {
            trigger.execute(this::runNow);
        } catch (RuntimeException e) {
            log.warn("Could not trigger categorization run", e);
        }
    }

    /** Processes due PENDING rows until none are left (bounded per run). Safe to call concurrently. */
    public RunStats runNow() {
        if (!running.compareAndSet(false, true)) {
            return RunStats.empty();
        }
        RunStats total = RunStats.empty();
        try {
            for (int i = 0; i < maxBatchesPerRun; i++) {
                List<Claimed> claimed = tx.execute(status -> claim());
                if (claimed == null || claimed.isEmpty()) {
                    break;
                }
                total = total.plus(process(claimed));
            }
        } catch (RuntimeException e) {
            // The worker must never take the application down
            log.error("Categorization run failed", e);
        } finally {
            running.set(false);
        }
        if (total.claimed() > 0) {
            log.info("Categorization run: claimed={}, categorized={}, uncategorizable={}, retryLater={}, failed={}",
                    total.claimed(), total.categorized(), total.uncategorizable(), total.retryLater(), total.failed());
        }
        return total;
    }

    // ---- step 1: claim ----------------------------------------------------------------------------------

    private List<Claimed> claim() {
        LocalDateTime now = now();
        List<Transaction> rows = repository.lockDuePending(now, now.minus(lease), claimSize);
        List<Claimed> result = new ArrayList<>();
        for (Transaction row : rows) {
            row.setClaimedAt(now);
            BankConnection connection = row.getBankAccount() == null ? null : row.getBankAccount().getBankConnection();
            User user = connection == null ? null : connection.getUser();
            result.add(new Claimed(row.getId(), user == null ? null : user.getId(),
                    connection == null ? null : connection.getInstitutionId(),
                    user == null || user.getName() == null ? List.of() : List.of(user.getName()),
                    row.getAmount(), row.getDescription(), row.getCounterpartyName(), row.getBankTypeCode()));
        }
        return result;
    }

    // ---- step 2: resolve (no transaction) -----------------------------------------------------------------

    private RunStats process(List<Claimed> claimed) {
        List<ParsedTransaction> parsed = new ArrayList<>();
        List<CategorizationOutcome> outcomes;
        try {
            for (Claimed c : claimed) {
                parsed.add(parsers.parse(new RawTransaction(c.institutionId(), c.amount(), c.description(),
                        c.counterparty(), c.typeCode(), c.holderNames())));
            }
            List<CategorizationRequest> requests = new ArrayList<>();
            for (int i = 0; i < claimed.size(); i++) {
                requests.add(new CategorizationRequest(claimed.get(i).userId(), parsed.get(i)));
            }
            outcomes = pipeline.categorize(requests);
        } catch (RuntimeException e) {
            log.error("Categorization of {} claimed rows failed unexpectedly", claimed.size(), e);
            parsed.clear();
            outcomes = null;
        }

        List<ParsedTransaction> finalParsed = parsed;
        List<CategorizationOutcome> finalOutcomes = outcomes;
        return tx.execute(status -> persist(claimed, finalParsed, finalOutcomes));
    }

    // ---- step 3: persist ----------------------------------------------------------------------------------

    private RunStats persist(List<Claimed> claimed, List<ParsedTransaction> parsed,
                             List<CategorizationOutcome> outcomes) {
        Map<Long, Transaction> rows = repository.findAllById(claimed.stream().map(Claimed::id).toList()).stream()
                .collect(Collectors.toMap(Transaction::getId, t -> t));
        LocalDateTime now = now();
        int categorized = 0;
        int uncategorizable = 0;
        int retryLater = 0;
        int failed = 0;
        for (int i = 0; i < claimed.size(); i++) {
            Transaction row = rows.get(claimed.get(i).id());
            // Skip rows that were decided meanwhile (e.g. a user override) or deleted
            if (row == null || row.getCategoryStatus() != CategoryStatus.PENDING) {
                continue;
            }
            row.setClaimedAt(null);
            CategorizationOutcome outcome = outcomes == null
                    ? new CategorizationOutcome(Category.OTHER.key(), CategoryStatus.FAILED, null) : outcomes.get(i);
            if (parsed.size() > i) {
                row.setMerchant(parsed.get(i).merchantKey());
            }
            switch (outcome.status()) {
                case CATEGORIZED -> {
                    finish(row, outcome, now);
                    categorized++;
                }
                case UNCATEGORIZABLE -> {
                    finish(row, outcome, now);
                    uncategorizable++;
                }
                case PENDING -> {
                    // The AI was not even asked (circuit open): wait without using up a retry
                    row.setNextRetryAt(now.plus(deferDelay));
                    retryLater++;
                }
                default -> {
                    int attempts = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
                    row.setRetryCount(attempts);
                    if (attempts >= maxRetries) {
                        row.setCategory(Category.OTHER.key());
                        row.setCategoryStatus(CategoryStatus.FAILED);
                        row.setCategorySource(null);
                        // The nightly job re-queues FAILED rows once this time has come (1 day, doubling, max 7)
                        row.setNextRetryAt(now.plus(failedRetryDelay(attempts - maxRetries + 1)));
                        failed++;
                    } else {
                        row.setNextRetryAt(now.plus(backoff(attempts)));
                        retryLater++;
                    }
                }
            }
        }
        return new RunStats(claimed.size(), categorized, uncategorizable, retryLater, failed);
    }

    private static void finish(Transaction row, CategorizationOutcome outcome, LocalDateTime now) {
        row.setCategory(outcome.category());
        row.setCategoryStatus(outcome.status());
        row.setCategorySource(outcome.source());
        row.setCategorizerVersion(CategorizationPipeline.VERSION);
        // UNCATEGORIZABLE rows are looked at again rarely, by the nightly job
        row.setNextRetryAt(outcome.status() == CategoryStatus.UNCATEGORIZABLE ? now.plus(UNCATEGORIZABLE_RETRY_AFTER) : null);
    }

    /** Wait before the nightly job retries a FAILED row for the nth time: 1 day, doubling, at most 7 days. */
    static Duration failedRetryDelay(int nth) {
        Duration delay = FAILED_RETRY_BASE.multipliedBy(1L << Math.min(10, Math.max(0, nth - 1)));
        return delay.compareTo(FAILED_RETRY_CAP) > 0 ? FAILED_RETRY_CAP : delay;
    }

    /** retryBaseDelay * 2^(attempt-1), capped at one day. */
    Duration backoff(int attempt) {
        long factor = 1L << Math.min(20, Math.max(0, attempt - 1));
        Duration delay = retryBaseDelay.multipliedBy(factor);
        return delay.compareTo(Duration.ofDays(1)) > 0 ? Duration.ofDays(1) : delay;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
