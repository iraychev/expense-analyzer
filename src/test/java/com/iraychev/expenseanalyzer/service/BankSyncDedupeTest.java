package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BankSyncDedupeTest {
    private static final LocalDateTime DATE = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private BankConnectionService service;

    private BankAccount account(Transaction... existing) {
        return BankAccount.builder().transactions(new ArrayList<>(List.of(existing))).build();
    }

    private Transaction stored(String externalId, String description) {
        return Transaction.builder().externalId(externalId).transactionDate(DATE)
                .amount(new BigDecimal("2.50")).description(description).build();
    }

    private TransactionDto fetched(String externalId, String description) {
        return TransactionDto.builder().externalId(externalId).transactionDate(DATE)
                .amount(new BigDecimal("2.5")).description(description).build();
    }

    private void mapperCopiesDto() {
        lenient().when(transactionMapper.toEntity(any())).thenAnswer(invocation -> {
            TransactionDto dto = invocation.getArgument(0);
            return Transaction.builder().externalId(dto.getExternalId()).description(dto.getDescription()).build();
        });
    }

    @Test
    void repeatedIdenticalPurchasesWithDifferentIdsAreBothKept() {
        mapperCopiesDto();

        List<Transaction> result = service.findNewTransactions(account(),
                List.of(fetched("a", "coffee"), fetched("b", "coffee")));

        assertEquals(2, result.size());
    }

    @Test
    void resyncIsIdempotent() {
        mapperCopiesDto();

        List<Transaction> result = service.findNewTransactions(account(stored("a", "coffee"), stored("b", "coffee")),
                List.of(fetched("a", "coffee"), fetched("b", "coffee")));

        assertEquals(0, result.size());
    }

    @Test
    void rowsWithoutIdAreMatchedOnceEachAndGetTheirIdBackfilled() {
        mapperCopiesDto();
        Transaction legacy = stored(null, "coffee");

        List<Transaction> result = service.findNewTransactions(account(legacy),
                List.of(fetched("a", "coffee"), fetched("b", "coffee")));

        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getExternalId());
        assertEquals("a", legacy.getExternalId());
    }
}
