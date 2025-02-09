package com.iraychev.expenseanalyzer.service;

import com.gocardless.resources.Payment;
import com.iraychev.expenseanalyzer.entity.BankAccount;
import com.iraychev.expenseanalyzer.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionSyncService {
    private final GoCardlessService goCardlessService;
    private final TransactionService transactionService;
    private final UserService userService;
    private final TransactionCategoryService categoryService;

    @Scheduled(cron = "0 0 */12 * * *") // Run every 12 hours
    public void scheduledSync() {
        List<BankAccount> activeAccounts = bankAccountRepository.findByStatus("ACTIVE");
        activeAccounts.forEach(this::syncTransactions);
    }

    public void syncTransactions(BankAccount bankAccount) {
        try {
            // Fetch new transactions from GoCardless
            List<Payment> newTransactions = goCardlessService.fetchPayments(bankAccount.getAccountId());

            // Process each transaction
            newTransactions.forEach(payment -> {
                // Check if transaction already exists
                if (!transactionService.existsByExternalId(payment.getId())) {
                    Transaction transaction = Transaction.builder()
                            .accountId(bankAccount.getAccountId())
                            .externalId(payment.getId())
                            .bookingDate(LocalDate.parse(payment.getCreatedAt().substring(0, 10)))
                            .amount(new BigDecimal(payment.getAmount()))
                            .currency(payment.getCurrency().toString())
                            .creditorName(payment.getDescription())
                            .category(categoryService.categorizeTransaction(payment.getDescription()))
                            .user(bankAccount.getUser())
                            .build();

                    transactionService.saveTransaction(transaction);
                }
            });

            log.info("Successfully synced {} transactions for account {}",
                    newTransactions.size(), bankAccount.getAccountId());

        } catch (Exception e) {
            log.error("Failed to sync transactions for account {}",
                    bankAccount.getAccountId(), e);
        }
    }
}
