package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
//TODO: delete or move this too
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final BankConnectionRepository bankConnectionRepository;

    public List<Transaction> saveTransactions(List<Transaction> transactions) {
        return transactionRepository.saveAll(transactions);
    }

    public Page<Transaction> getUserTransactions(Long userId, Pageable pageable) {
        return transactionRepository.findByBankConnection_User_Id(userId, pageable);
    }

    public TransactionType determineTransactionType(String goCardlessType) {
        return switch (goCardlessType.toLowerCase()) {
            case "credit" -> TransactionType.INCOME;
            case "debit" -> TransactionType.EXPENSE;
            default -> TransactionType.TRANSFER;
        };
    }
}