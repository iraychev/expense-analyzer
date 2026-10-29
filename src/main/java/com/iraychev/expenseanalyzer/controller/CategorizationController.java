package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.categorization.CategorizationWorker;
import com.iraychev.expenseanalyzer.dto.OtherRetryRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1/categorization")
@RequiredArgsConstructor
public class CategorizationController {

    private final CategorizationWorker worker;

    /**
     * Runs the categorization worker now. Kept at its old path and response shape: processedCount is the number of
     * claimed rows, recategorizedCount those that got a category, stillOtherCount those that could not be
     * categorized (yet) and maxRetriesReachedCount those that failed for good.
     */
    @PostMapping("/other-retry/run")
    public ResponseEntity<OtherRetryRunResponse> runOtherRetryNow() {
        CategorizationWorker.RunStats stats = worker.runNow();
        return ResponseEntity.ok(new OtherRetryRunResponse(
                LocalDateTime.now(ZoneOffset.UTC),
                stats.claimed(),
                stats.categorized(),
                stats.uncategorizable() + stats.retryLater() + stats.failed(),
                stats.failed()));
    }
}
