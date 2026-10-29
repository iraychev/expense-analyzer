package com.iraychev.expenseanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GoCardlessExternalIdTest {

    @Test
    void everySampleTransactionCarriesAUniqueExternalId() {
        GoCardlessIntegrationService service = new GoCardlessIntegrationService(null, new ObjectMapper());

        for (String iban : List.of("BG16UNCR70001598168484", "LT143250059635469546", "BG68STSA93000027489927")) {
            BankAccountDto account = BankAccountDto.builder().id(1L).iban(iban).accountId("a").build();
            List<TransactionDto> txs = service.getCachedTransactions(List.of(account)).get(0).getTransactions();
            assertNotNull(txs);
            Set<String> ids = new HashSet<>();
            txs.forEach(t -> {
                assertNotNull(t.getExternalId(), "missing id in " + iban);
                ids.add(t.getExternalId());
            });
            assertEquals(txs.size(), ids.size(), "duplicate ids in " + iban);
        }
    }
}
