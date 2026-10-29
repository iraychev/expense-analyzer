package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.categorization.parser.TransactionParsers;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Parses stored transactions and runs them through the {@link CategorizationPipeline}, updating the entities. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionCategorizationService {

    private final TransactionParsers parsers;
    private final CategorizationPipeline pipeline;

    /**
     * Categorizes the given transactions of one bank connection and writes merchant, category, status, source and
     * version onto them. Never throws because of the AI: failures end up as FAILED rows.
     */
    public void categorize(List<Transaction> transactions, BankConnection connection) {
        if (transactions.isEmpty()) {
            return;
        }
        User user = connection == null ? null : connection.getUser();
        List<String> holderNames = user == null || user.getName() == null ? List.of() : List.of(user.getName());
        String institutionId = connection == null ? null : connection.getInstitutionId();
        Long userId = user == null ? null : user.getId();

        List<CategorizationRequest> requests = transactions.stream()
                .map(tx -> new CategorizationRequest(userId, parsers.parse(new RawTransaction(
                        institutionId, tx.getAmount(), tx.getDescription(), tx.getCounterpartyName(),
                        tx.getBankTypeCode(), holderNames))))
                .toList();

        List<CategorizationOutcome> outcomes = pipeline.categorize(requests);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (int i = 0; i < transactions.size(); i++) {
            apply(transactions.get(i), requests.get(i).parsed(), outcomes.get(i), now);
        }
        log.info("Categorized {} transactions", transactions.size());
    }

    static void apply(Transaction tx, ParsedTransaction parsed, CategorizationOutcome outcome, LocalDateTime now) {
        tx.setMerchant(parsed.merchantKey());
        tx.setCategory(outcome.category());
        tx.setCategoryStatus(outcome.status());
        tx.setCategorySource(outcome.source());
        tx.setCategorizerVersion(CategorizationPipeline.VERSION);
        // Keep the legacy retry bookkeeping working until the async worker takes over
        tx.setOtherRetryCount(Objects.requireNonNullElse(tx.getOtherRetryCount(), 0));
        if (outcome.status() == CategoryStatus.CATEGORIZED) {
            tx.setOtherNextRetryAt(null);
        } else if (tx.getOtherNextRetryAt() == null) {
            tx.setOtherNextRetryAt(now.plusDays(1));
        }
    }
}
