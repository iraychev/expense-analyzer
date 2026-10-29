package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Nightly maintenance: puts rows back in the worker queue and logs how the data is distributed.
 * <ul>
 *   <li>FAILED rows once their backoff has passed (1 day, doubling up to 7 days; the failure count is kept);</li>
 *   <li>UNCATEGORIZABLE rows rarely (30 days after the last attempt), with a fresh failure count;</li>
 *   <li>automatically categorized rows produced by an older {@link CategorizationPipeline#VERSION}, a bounded number
 *       per night; their current category stays visible until the worker replaces it.</li>
 * </ul>
 * The worker does the actual categorizing and honours user choices, which are never requeued.
 */
@Component
@Slf4j
public class CategorizationMaintenanceJob {

    /** What one night did. */
    public record Summary(int failedRequeued, int uncategorizableRequeued, int outdatedRequeued,
                          Map<String, Long> countsByStatusAndSource) {
    }

    private final TransactionRepository repository;
    private final CategorizationWorker worker;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final int versionBatchSize;

    public CategorizationMaintenanceJob(TransactionRepository repository, CategorizationWorker worker,
                                        PlatformTransactionManager transactionManager,
                                        @Value("${categorization.nightly.batch-size:500}") int batchSize,
                                        @Value("${categorization.nightly.version-batch-size:500}") int versionBatchSize) {
        this.repository = repository;
        this.worker = worker;
        this.tx = new TransactionTemplate(transactionManager);
        this.batchSize = Math.max(1, batchSize);
        this.versionBatchSize = Math.max(1, versionBatchSize);
    }

    @Scheduled(cron = "${categorization.nightly.cron:0 0 3 * * *}", zone = "${categorization.nightly.zone:UTC}")
    public void scheduledRun() {
        try {
            runNow();
        } catch (RuntimeException e) {
            log.error("Nightly categorization maintenance failed", e);
        }
    }

    public Summary runNow() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int[] requeued = tx.execute(status -> new int[]{
                requeue(CategoryStatus.FAILED, now, false),
                requeue(CategoryStatus.UNCATEGORIZABLE, now, true),
                requeueOutdated()});
        Map<String, Long> counts = tx.execute(status -> countsByStatusAndSource());

        log.info("Nightly categorization: requeued failed={}, uncategorizable={}, outdated(version<{})={}; rows by status/source: {}",
                requeued[0], requeued[1], CategorizationPipeline.VERSION, requeued[2], counts);
        if (requeued[0] + requeued[1] + requeued[2] > 0) {
            worker.triggerAsync();
        }
        return new Summary(requeued[0], requeued[1], requeued[2], counts);
    }

    private int requeue(CategoryStatus status, LocalDateTime now, boolean resetRetries) {
        List<Long> ids = repository.findRetryDueIds(status, now, PageRequest.of(0, batchSize));
        if (ids.isEmpty()) {
            return 0;
        }
        return resetRetries
                ? repository.requeueResettingRetries(ids, CategoryStatus.PENDING)
                : repository.requeueKeepingRetries(ids, CategoryStatus.PENDING);
    }

    private int requeueOutdated() {
        List<Long> ids = repository.findOutdatedIds(CategoryStatus.CATEGORIZED, CategorySource.USER,
                CategorizationPipeline.VERSION, PageRequest.of(0, versionBatchSize));
        return ids.isEmpty() ? 0 : repository.requeueResettingRetries(ids, CategoryStatus.PENDING);
    }

    private Map<String, Long> countsByStatusAndSource() {
        Map<String, Long> counts = new TreeMap<>();
        for (Object[] row : repository.countByStatusAndSource()) {
            counts.put(row[0] + "/" + (row[1] == null ? "-" : row[1]), (Long) row[2]);
        }
        return counts;
    }
}
