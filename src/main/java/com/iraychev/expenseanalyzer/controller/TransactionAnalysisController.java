package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.AnalysisItemDto;
import com.iraychev.expenseanalyzer.service.TransactionAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Slf4j
public class TransactionAnalysisController {
    private final TransactionAnalysisService transactionAnalysisService;

    @GetMapping
    public ResponseEntity<List<AnalysisItemDto>> getTransactionAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        log.info("Generating transaction analysis for user {} between {} and {}",
                 username, startDate, endDate);

        return ResponseEntity.ok(transactionAnalysisService.generateTransactionAnalysis(username, startDate, endDate));
    }
}
