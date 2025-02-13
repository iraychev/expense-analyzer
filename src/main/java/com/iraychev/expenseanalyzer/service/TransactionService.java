package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final TransactionMapper transactionMapper;

    public List<TransactionDto> saveTransactions(List<TransactionDto> transactionsDtos) {

        List<Transaction> transactions = transactionsDtos.stream().map(transactionMapper::toEntity).toList();
        List<Transaction> savedTransactions = transactionRepository.saveAll(transactions);
        return savedTransactions.stream().map(transactionMapper::toDTO).toList();
    }
}