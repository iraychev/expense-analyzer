package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.mapper.BankAccountMapper;
import com.iraychev.expenseanalyzer.mapper.BankConnectionMapper;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Service-level check of the sync merge using the real MapStruct mapper and a stubbed bank. */
class BankSyncIdempotencyTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 3, 1, 0, 0);

    private final List<TransactionDto> bank = new ArrayList<>();
    private BankConnectionService service;
    private BankAccount account;

    @BeforeEach
    void setUp() {
        GoCardlessIntegrationService goCardless = mock(GoCardlessIntegrationService.class);
        UserRepository userRepository = mock(UserRepository.class);
        BankConnectionRepository connectionRepository = mock(BankConnectionRepository.class);
        BankAccountRepository accountRepository = mock(BankAccountRepository.class);
        BankAccountMapper accountMapper = mock(BankAccountMapper.class);
        BankConnectionMapper connectionMapper = mock(BankConnectionMapper.class);
        TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);

        account = BankAccount.builder().id(1L).accountId("acc").transactions(new ArrayList<>()).build();
        BankConnection connection = BankConnection.builder().id(2L).requisitionId("req")
                .accounts(new ArrayList<>(List.of(account))).build();
        account.setBankConnection(connection);

        when(userRepository.findByUsername("u")).thenReturn(Optional.of(new User()));
        when(connectionRepository.findByRequisitionId("req")).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(accountMapper.toDTO(any(BankAccount.class))).thenAnswer(i -> BankAccountDto.builder().id(1L).build());
        when(connectionMapper.toDTO(any())).thenReturn(new BankConnectionDto());
        when(goCardless.updateBankAccountsWithFetchedTransactions(any())).thenAnswer(i -> {
            List<BankAccountDto> accounts = i.getArgument(0);
            accounts.get(0).setTransactions(new ArrayList<>(bank));
            return accounts;
        });

        service = new BankConnectionService(goCardless, userRepository, accountRepository, accountMapper,
                connectionRepository, connectionMapper, transactionMapper);
    }

    private static TransactionDto tx(String id) {
        return TransactionDto.builder().externalId(id).amount(new BigDecimal("-4.10")).currency("BGN")
                .valueDate(DAY).transactionDate(DAY).description("POS purchase same text").category("Other").build();
    }

    @Test
    void identicalRepeatedPurchasesAreBothKeptAndResyncAddsNothing() {
        bank.add(tx("t1"));
        bank.add(tx("t2"));

        service.updateBankConnection("u", "req", false);
        assertEquals(2, account.getTransactions().size());

        service.updateBankConnection("u", "req", false);
        assertEquals(2, account.getTransactions().size());

        bank.add(tx("t3"));
        service.updateBankConnection("u", "req", false);
        assertEquals(3, account.getTransactions().size());
    }

    @Test
    void legacyRowsWithoutExternalIdAreNotDuplicatedAndGetBackfilled() {
        Transaction legacy = Transaction.builder()
                .amount(new BigDecimal("-4.1")).currency("BGN").valueDate(DAY).transactionDate(DAY)
                .description("POS purchase same text").bankAccount(account).build();
        account.getTransactions().add(legacy);
        bank.add(tx("t1"));
        bank.add(tx("t2"));

        service.updateBankConnection("u", "req", false);

        assertEquals(2, account.getTransactions().size());
        assertEquals("t1", legacy.getExternalId());
    }
}
