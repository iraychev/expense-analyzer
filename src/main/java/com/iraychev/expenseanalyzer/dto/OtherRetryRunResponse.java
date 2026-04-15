package com.iraychev.expenseanalyzer.dto;

import java.time.LocalDateTime;

public record OtherRetryRunResponse(
        LocalDateTime executedAt,
        int processedCount,
        int recategorizedCount,
        int stillOtherCount,
        int maxRetriesReachedCount
) {
}

