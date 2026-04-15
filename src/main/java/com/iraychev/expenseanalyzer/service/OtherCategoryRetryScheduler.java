package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.dto.OtherRetryRunResponse;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtherCategoryRetryScheduler {

    private static final String OTHER_CATEGORY = "Other";

    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;

    @Value("${categorization.other-retry.max-retries:3}")
    private int maxRetries;

    @Value("${categorization.other-retry.batch-size:50}")
    private int batchSize;

    @Scheduled(
            cron = "${categorization.other-retry.cron:0 0 3 * * *}",
            zone = "${categorization.other-retry.zone:UTC}"
    )
    @Transactional
    public void retryOtherCategories() {
        OtherRetryRunResponse result = executeRetryBatch();
        if (result.processedCount() > 0) {
            log.info("Processed {} due 'Other' retries (recategorized={}, stillOther={}, maxRetriesReached={})",
                    result.processedCount(),
                    result.recategorizedCount(),
                    result.stillOtherCount(),
                    result.maxRetriesReachedCount());
        }
    }

    @Transactional
    public OtherRetryRunResponse retryOtherCategoriesNow() {
        return executeRetryBatch();
    }

    private OtherRetryRunResponse executeRetryBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> dueTransactions = transactionRepository.findDueOtherRetries(
                now,
                maxRetries,
                PageRequest.of(0, batchSize)
        );

        if (dueTransactions.isEmpty()) {
            return new OtherRetryRunResponse(now, 0, 0, 0, 0);
        }

        int recategorizedCount = 0;
        int stillOtherCount = 0;
        int maxRetriesReachedCount = 0;

        for (Transaction transaction : dueTransactions) {
            int retryCount = transaction.getOtherRetryCount() == null ? 0 : transaction.getOtherRetryCount();
            transaction.setOtherRetryCount(retryCount);
            String description = transaction.getDescription();
            String newCategory = categoryService.categorizeTransaction(description);

            if (!OTHER_CATEGORY.equalsIgnoreCase(newCategory)) {
                transaction.setCategory(newCategory);
                transaction.setOtherNextRetryAt(null);
                recategorizedCount += 1;
                continue;
            }

            stillOtherCount += 1;
            retryCount += 1;
            transaction.setOtherRetryCount(retryCount);
            if (retryCount >= maxRetries) {
                transaction.setOtherNextRetryAt(null);
                maxRetriesReachedCount += 1;
            } else {
                transaction.setOtherNextRetryAt(now.plus(getBackoffForRetry(retryCount)));
            }
        }

        transactionRepository.saveAll(dueTransactions);
        return new OtherRetryRunResponse(
                now,
                dueTransactions.size(),
                recategorizedCount,
                stillOtherCount,
                maxRetriesReachedCount
        );
    }

    private Duration getBackoffForRetry(int retryCount) {
        return switch (retryCount) {
            case 1 -> Duration.ofDays(1);
            case 2 -> Duration.ofDays(3);
            default -> Duration.ofDays(7);
        };
    }
}




