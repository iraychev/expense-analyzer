package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.categorization.Category;
import com.iraychev.expenseanalyzer.categorization.CategorizationPipeline;
import com.iraychev.expenseanalyzer.categorization.RawTransaction;
import com.iraychev.expenseanalyzer.categorization.parser.TransactionParsers;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.domain.entity.UserCategoryRule;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import com.iraychev.expenseanalyzer.dto.CategoryUpdateRequest;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.exception.ValidationException;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.TransactionRepository;
import com.iraychev.expenseanalyzer.repository.UserCategoryRuleRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Lets a user correct the category of a transaction, optionally for the whole merchant. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionCategoryService {

    private final TransactionRepository transactions;
    private final UserCategoryRuleRepository rules;
    private final UserRepository users;
    private final TransactionParsers parsers;
    private final TransactionMapper mapper;

    /**
     * @throws ResourceNotFoundException (404) if the transaction does not exist or belongs to someone else
     * @throws ValidationException       (400) for an unknown category
     */
    @Transactional
    public TransactionDto updateCategory(String username, Long transactionId, CategoryUpdateRequest request) {
        Category category = request == null ? null : Category.fromKey(request.getCategory()).orElse(null);
        if (category == null) {
            throw new ValidationException("category", "Unknown category");
        }
        User user = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Transaction tx = transactions.findById(transactionId)
                .filter(t -> ownedBy(t, user))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (tx.getMerchant() == null) {
            tx.setMerchant(parseMerchant(tx, user));
        }
        applyUserChoice(tx, category);

        if (request.isApplyToMerchant() && tx.getMerchant() != null) {
            UserCategoryRule rule = rules.findByUserIdAndMerchant(user.getId(), tx.getMerchant())
                    .orElseGet(() -> UserCategoryRule.builder().userId(user.getId()).merchant(tx.getMerchant()).build());
            rule.setCategory(category.key());
            rule.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            rules.save(rule);

            List<Transaction> sameMerchant = new java.util.ArrayList<>(
                    transactions.findByUserAndMerchant(user.getId(), tx.getMerchant()));
            // Legacy rows were stored before merchants were parsed
            for (Transaction legacy : transactions.findByUserWithoutMerchant(user.getId())) {
                String merchant = parseMerchant(legacy, user);
                legacy.setMerchant(merchant);
                if (tx.getMerchant().equals(merchant)) {
                    sameMerchant.add(legacy);
                }
            }
            int changed = 0;
            for (Transaction other : sameMerchant) {
                // Individual choices the user made earlier stay as they are
                if (other != tx && other.getCategorySource() != CategorySource.USER) {
                    applyUserChoice(other, category);
                    changed++;
                }
            }
            log.info("User {} set merchant {} to {}: {} other transaction(s) updated", user.getId(), tx.getMerchant(),
                    category.key(), changed);
        }
        return mapper.toDTO(tx);
    }

    private static boolean ownedBy(Transaction tx, User user) {
        BankConnection connection = tx.getBankAccount() == null ? null : tx.getBankAccount().getBankConnection();
        return connection != null && connection.getUser() != null && user.getId().equals(connection.getUser().getId());
    }

    private String parseMerchant(Transaction tx, User user) {
        BankConnection connection = tx.getBankAccount().getBankConnection();
        return parsers.parse(new RawTransaction(connection.getInstitutionId(), tx.getAmount(), tx.getDescription(),
                tx.getCounterpartyName(), tx.getBankTypeCode(),
                user.getName() == null ? List.of() : List.of(user.getName()))).merchantKey();
    }

    private static void applyUserChoice(Transaction tx, Category category) {
        tx.setCategory(category.key());
        tx.setCategoryStatus(CategoryStatus.CATEGORIZED);
        tx.setCategorySource(CategorySource.USER);
        tx.setCategorizerVersion(CategorizationPipeline.VERSION);
        tx.setRetryCount(0);
        tx.setNextRetryAt(null);
        tx.setClaimedAt(null);
    }
}
