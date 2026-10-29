package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.TransactionDto;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which fetched bank transactions are new for an account.
 * <p>
 * Rows that carry the bank's transaction id are matched by that id. Legacy rows that were stored before the id was
 * recorded are matched as a multiset on (valueDate, amount, description): every legacy row can absorb at most one
 * fetched transaction, so two identical purchases are still counted twice, and the matched legacy row gets its
 * external id backfilled.
 */
final class TransactionMerger {

    private TransactionMerger() {
    }

    /**
     * @return the fetched transactions that are not yet stored. Legacy rows that matched are updated in place.
     */
    static List<TransactionDto> selectNew(List<Transaction> existing, List<TransactionDto> fetched) {
        Set<String> knownIds = new HashSet<>();
        Map<String, Deque<Transaction>> legacyPool = new HashMap<>();
        for (Transaction tx : existing) {
            if (tx.getExternalId() != null && !tx.getExternalId().isBlank()) {
                knownIds.add(tx.getExternalId());
            } else {
                legacyPool.computeIfAbsent(legacyKey(tx.getValueDate() == null ? null : tx.getValueDate().toString(),
                                tx.getAmount() == null ? null : tx.getAmount().stripTrailingZeros().toPlainString(),
                                tx.getDescription()), k -> new ArrayDeque<>())
                        .add(tx);
            }
        }

        List<TransactionDto> result = new ArrayList<>();
        for (TransactionDto dto : fetched) {
            String externalId = dto.getExternalId() == null || dto.getExternalId().isBlank() ? null : dto.getExternalId();
            if (externalId != null && knownIds.contains(externalId)) {
                continue;
            }

            Deque<Transaction> candidates = legacyPool.get(legacyKey(
                    dto.getValueDate() == null ? null : dto.getValueDate().toString(),
                    dto.getAmount() == null ? null : dto.getAmount().stripTrailingZeros().toPlainString(),
                    dto.getDescription()));
            if (candidates != null && !candidates.isEmpty()) {
                Transaction legacy = candidates.poll();
                if (externalId != null) {
                    legacy.setExternalId(externalId);
                    knownIds.add(externalId);
                }
                continue;
            }

            if (externalId != null) {
                knownIds.add(externalId);
            }
            result.add(dto);
        }
        return result;
    }

    private static String legacyKey(String valueDate, String amount, String description) {
        return valueDate + "|" + amount + "|" + (description == null ? "" : description);
    }
}
