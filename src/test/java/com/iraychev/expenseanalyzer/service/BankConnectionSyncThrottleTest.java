package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.categorization.parser.TransactionParsers;
import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.exception.RateLimitException;
import com.iraychev.expenseanalyzer.mapper.BankAccountMapper;
import com.iraychev.expenseanalyzer.mapper.BankConnectionMapper;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class BankConnectionSyncThrottleTest {

    @Mock private GoCardlessIntegrationService goCardless;
    @Mock private UserRepository userRepository;
    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private BankAccountMapper bankAccountMapper;
    @Mock private BankConnectionRepository bankConnectionRepository;
    @Mock private BankConnectionMapper bankConnectionMapper;
    @Mock private TransactionMapper transactionMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TransactionParsers parsers;

    private BankConnectionService service;
    private BankAccount account;

    @BeforeEach
    void setUp() {
        service = new BankConnectionService(goCardless, userRepository, bankAccountRepository, bankAccountMapper,
                bankConnectionRepository, bankConnectionMapper, transactionMapper, eventPublisher, parsers);
        account = BankAccount.builder().id(1L).accountId("acc-1").transactions(new ArrayList<>()).build();
        BankConnection connection = BankConnection.builder().id(5L).requisitionId("req-1")
                .accounts(new ArrayList<>(List.of(account))).build();
        account.setBankConnection(connection);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(new User()));
        when(bankConnectionRepository.findByRequisitionId("req-1")).thenReturn(Optional.of(connection));
        when(bankConnectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(bankConnectionMapper.toDTO(any())).thenAnswer(i -> new BankConnectionDto());
    }

    private void stubFetch() {
        when(bankAccountMapper.toDTO(account))
                .thenReturn(BankAccountDto.builder().id(1L).transactions(new ArrayList<>()).build());
        when(goCardless.updateBankAccountsWithFetchedTransactions(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void recentlySyncedAccountIsNotFetchedAgain() {
        Instant last = Instant.now().minus(Duration.ofHours(1));
        account.setLastSyncedAt(last);

        BankConnectionDto result = service.updateBankConnection("bob", "req-1", false);

        verify(goCardless, never()).updateBankAccountsWithFetchedTransactions(any());
        verify(goCardless, never()).getRequisition(any());
        assertTrue(result.getThrottled());
        assertEquals(last.plus(Duration.ofHours(6)), result.getNextSyncAvailableAt());
        assertEquals(last, account.getLastSyncedAt());
    }

    @Test
    void staleAccountIsFetchedAndTimestampUpdated() {
        stubFetch();
        account.setLastSyncedAt(Instant.now().minus(Duration.ofHours(7)));
        Instant before = Instant.now();

        BankConnectionDto result = service.updateBankConnection("bob", "req-1", false);

        verify(goCardless).updateBankAccountsWithFetchedTransactions(any());
        assertFalse(result.getThrottled());
        assertFalse(account.getLastSyncedAt().isBefore(before));
    }

    @Test
    void neverSyncedAccountIsFetched() {
        stubFetch();
        service.updateBankConnection("bob", "req-1", false);
        verify(goCardless).updateBankAccountsWithFetchedTransactions(any());
        assertNotNull(account.getLastSyncedAt());
    }

    @Test
    void intervalIsConfigurable() {
        ReflectionTestUtils.setField(service, "minInterval", Duration.ofMinutes(10));
        stubFetch();
        account.setLastSyncedAt(Instant.now().minus(Duration.ofMinutes(30)));
        service.updateBankConnection("bob", "req-1", false);
        verify(goCardless).updateBankAccountsWithFetchedTransactions(any());
    }

    @Test
    void rateLimitFromBankIsPropagatedAsTooManyRequests() {
        when(bankAccountMapper.toDTO(account)).thenReturn(BankAccountDto.builder().id(1L).build());
        when(goCardless.updateBankAccountsWithFetchedTransactions(any())).thenThrow(new RateLimitException());

        RateLimitException e = assertThrows(RateLimitException.class,
                () -> service.updateBankConnection("bob", "req-1", false));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        assertEquals("ERR007", e.getErrorCode());
        assertNull(account.getLastSyncedAt());
    }

    @Test
    void finalizeOfAlreadyLinkedRequisitionDoesNotCreateAnotherConnectionOrRefetch() {
        account.setLastSyncedAt(Instant.now().minus(Duration.ofMinutes(5)));

        service.updateBankConnection("bob", "req-1", true);

        verify(goCardless, never()).getRequisition(any());
        verify(goCardless, never()).updateBankAccountsWithFetchedTransactions(any());
    }
}
