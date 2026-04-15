package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.OtherRetryRunResponse;
import com.iraychev.expenseanalyzer.service.OtherCategoryRetryScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categorization")
@RequiredArgsConstructor
public class CategorizationController {

    private final OtherCategoryRetryScheduler otherCategoryRetryScheduler;

    @PostMapping("/other-retry/run")
    public ResponseEntity<OtherRetryRunResponse> runOtherRetryNow() {
        OtherRetryRunResponse result = otherCategoryRetryScheduler.retryOtherCategoriesNow();
        return ResponseEntity.ok(result);
    }
}


