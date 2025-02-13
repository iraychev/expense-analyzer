package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.service.GoCardlessIntegrationService;
import com.iraychev.expenseanalyzer.service.TransactionService;
import com.iraychev.expenseanalyzer.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bank-accounts")
@Slf4j
@RequiredArgsConstructor
// TODO: delete or move to user controller
public class TransactionController {
    private final UserService userService;
    private final GoCardlessIntegrationService goCardlessService;
    private final TransactionService transactionService;

    @GetMapping("/user/{userId}/transactions")
    @REsponseStatus(OK)
    public Page<TransactionDto> getUserTransactions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());
        Page<Transaction> transactionsPage = transactionService.getUserTransactions(userId, pageable);

        Page<TransactionDto> transactionDtos = transactionsPage.map(transaction ->
                TransactionDto.builder()
                        .id(transaction.getId())
                        .amount(transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .transactionDate(transaction.getTransactionDate())
                        .description(transaction.getDescription())
                        .type(transaction.getType())
                        .build()
        );

        return transactionDtos;
    }
}