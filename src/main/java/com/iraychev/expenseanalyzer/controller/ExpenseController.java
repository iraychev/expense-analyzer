package com.iraychev.expenseanalyzer.controller;

import com.gocardless.resources.Payment;
import com.iraychev.expenseanalyzer.entity.Transaction;
import com.iraychev.expenseanalyzer.entity.User;
import com.iraychev.expenseanalyzer.service.GoCardlessService;
import com.iraychev.expenseanalyzer.service.TransactionCategoryService;
import com.iraychev.expenseanalyzer.service.TransactionService;
import com.iraychev.expenseanalyzer.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExpenseController {
    private final UserService userService;
    private final TransactionService transactionService;
    private final GoCardlessService goCardlessService;
    private final TransactionCategoryService categoryService;

    @PostMapping("/users/{userId}/fetch-transactions")
    public ResponseEntity<String> fetchAndSaveTransactions(@PathVariable Long userId,
                                                           @RequestParam String accountId) {
        // Fetch external payments using the GoCardless service.
        List<Payment> externalPayments = goCardlessService.fetchPayments(accountId);
        User user = userService.findUserById(userId);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;

        // Iterate through the fetched payments and convert each to a Transaction entity.
        externalPayments.forEach(extPayment -> {
            // Assume extPayment.getCreatedAt() returns an ISO-8601 timestamp.
            LocalDate bookingDate = LocalDate.parse(extPayment.getCreatedAt().substring(0, 10), formatter);

            Transaction tx = Transaction.builder()
                    .accountId(extPayment.getId())
                    .bookingDate(bookingDate)
                    .amount(new BigDecimal(extPayment.getAmount()))
                    .currency(extPayment.getCurrency().toString())
                    .creditorName(extPayment.getDescription())
                    .category(categoryService.categorizeTransaction(extPayment.getDescription()))
                    .user(user)
                    .build();
            transactionService.saveTransaction(tx);
        });

        return ResponseEntity.ok("Transactions fetched and saved successfully");
    }

    @GetMapping("/users/{userId}/expenses/by-category")
    public ResponseEntity<Map<String, BigDecimal>> getExpensesByCategory(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(transactionService.getExpensesByCategory(userId, startDate, endDate));
    }

    @GetMapping("/users/{userId}/expenses/total")
    public ResponseEntity<BigDecimal> getTotalExpenses(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(transactionService.getTotalExpenses(userId, startDate, endDate));
    }
}