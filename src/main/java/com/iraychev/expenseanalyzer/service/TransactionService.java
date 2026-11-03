package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.exception.ValidationException;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    /** Lets a user correct the category of one of their own transactions. */
    @Transactional
    public TransactionDto updateCategory(String username, Long transactionId, String category) {
        if (category == null || !AiCategorizationService.VALID_CATEGORIES.contains(category)) {
            throw new ValidationException("category", "Please choose one of: "
                    + String.join(", ", AiCategorizationService.VALID_CATEGORIES));
        }
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(t -> t.getBankAccount().getBankConnection().getUser().getUsername().equals(username))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        transaction.setCategory(category);
        transaction.setOtherNextRetryAt(null);
        return transactionMapper.toDTO(transactionRepository.save(transaction));
    }
}
