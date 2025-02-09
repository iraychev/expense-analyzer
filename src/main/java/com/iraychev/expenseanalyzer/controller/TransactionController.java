package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.TransactionDTO;
import com.iraychev.expenseanalyzer.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // Get transactions for a user. Optionally, date range filters can be provided.
    @GetMapping
    public ResponseEntity<List<TransactionDTO>> getUserTransactions(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<TransactionDTO> transactions;
        if (startDate != null && endDate != null) {
            transactions = transactionService.getTransactionsByDateRange(userId, startDate, endDate);
        } else {
            transactions = transactionService.getTransactionsByUserId(userId);
        }
        return ResponseEntity.ok(transactions);
    }
}
