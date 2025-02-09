package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.dto.TransactionDTO;
import com.iraychev.expenseanalyzer.entity.Transaction;
import com.iraychev.expenseanalyzer.exception.InvalidDateRangeException;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public TransactionDTO saveTransaction(Transaction transaction) {
        Transaction saved = transactionRepository.save(transaction);
        return transactionMapper.toDTO(saved);
    }

    public List<TransactionDTO> getTransactionsByUserId(Long userId) {
        return transactionRepository.findByUserId(userId)
                .stream()
                .map(transactionMapper::toDTO)
                .collect(Collectors.toList());
    }

    public Map<String, BigDecimal> getExpensesByCategory(Long userId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByUserIdAndBookingDateBetween(userId, startDate, endDate)
                .stream()
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));
    }

    public BigDecimal getTotalExpenses(Long userId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByUserIdAndBookingDateBetween(userId, startDate, endDate)
                .stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<TransactionDTO> getTransactionsByDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("Start date must be before end date");
        }
        return transactionRepository.findByUserIdAndBookingDateBetween(userId, startDate, endDate)
                .stream()
                .map(transactionMapper::toDTO)
                .collect(Collectors.toList());
    }
}