package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.exception.InvalidDateRangeException;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;

    public List<Transaction> saveTransactions(List<Transaction> transactions) {
        return transactionRepository.saveAll(transactions);
    }

    public Page<Transaction> getUserTransactions(Long userId, Pageable pageable) {
        return transactionRepository.findByBankAccount_User_Id(userId, pageable);
    }

    public TransactionType determineTransactionType(String goCardlessType) {
        switch (goCardlessType.toLowerCase()) {
            case "credit":
                return TransactionType.INCOME;
            case "debit":
                return TransactionType.EXPENSE;
            default:
                return TransactionType.TRANSFER;
        }
    }
}