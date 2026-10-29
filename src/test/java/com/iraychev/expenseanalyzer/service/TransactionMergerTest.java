package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionMergerTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 3, 1, 0, 0);

    private static TransactionDto dto(String externalId, String amount, String description) {
        return TransactionDto.builder().externalId(externalId).amount(new BigDecimal(amount))
                .valueDate(DAY).transactionDate(DAY).description(description).build();
    }

    private static Transaction stored(String externalId, String amount, String description) {
        return Transaction.builder().externalId(externalId).amount(new BigDecimal(amount))
                .valueDate(DAY).transactionDate(DAY).description(description).build();
    }

    @Test
    void identicalPurchasesWithDistinctIdsAreAllKept() {
        List<TransactionDto> fetched = List.of(dto("a", "-4.10", "coffee"), dto("b", "-4.10", "coffee"));

        List<TransactionDto> result = TransactionMerger.selectNew(new ArrayList<>(), fetched);

        assertEquals(2, result.size());
    }

    @Test
    void resyncIsIdempotentById() {
        List<Transaction> existing = List.of(stored("a", "-4.10", "coffee"), stored("b", "-4.10", "coffee"));

        List<TransactionDto> result = TransactionMerger.selectNew(existing,
                List.of(dto("a", "-4.10", "coffee"), dto("b", "-4.10", "coffee"), dto("c", "-4.10", "coffee")));

        assertEquals(1, result.size());
        assertEquals("c", result.get(0).getExternalId());
    }

    @Test
    void legacyRowsAreMatchedAsMultisetAndBackfilled() {
        Transaction legacy1 = stored(null, "-4.10", "coffee");
        Transaction legacy2 = stored(null, "-4.1", "coffee");
        List<Transaction> existing = List.of(legacy1, legacy2);

        // two coffees are already stored, three are fetched: exactly one is new
        List<TransactionDto> result = TransactionMerger.selectNew(existing,
                List.of(dto("a", "-4.10", "coffee"), dto("b", "-4.10", "coffee"), dto("c", "-4.10", "coffee")));

        assertEquals(1, result.size());
        assertEquals("c", result.get(0).getExternalId());
        assertEquals("a", legacy1.getExternalId());
        assertEquals("b", legacy2.getExternalId());

        // after the backfill a second pass is a pure id match
        assertTrue(TransactionMerger.selectNew(existing,
                List.of(dto("a", "-4.10", "coffee"), dto("b", "-4.10", "coffee"))).isEmpty());
    }

    @Test
    void transactionsWithoutAnyIdFallBackToMultisetMatching() {
        Transaction legacy = stored(null, "-1.00", "x");

        List<TransactionDto> result = TransactionMerger.selectNew(List.of(legacy),
                List.of(dto(null, "-1.00", "x"), dto(null, "-1.00", "x")));

        assertEquals(1, result.size());
        assertNull(legacy.getExternalId());
    }

    @Test
    void sameDescriptionDifferentAmountIsNew() {
        List<TransactionDto> result = TransactionMerger.selectNew(List.of(stored(null, "-1.00", "x")),
                List.of(dto("z", "-2.00", "x")));
        assertEquals(1, result.size());
    }
}
