package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.categorization.TransactionCategorizationService;
import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
import com.iraychev.expenseanalyzer.exception.RateLimitException;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.exception.ValidationException;
import com.iraychev.expenseanalyzer.mapper.BankAccountMapper;
import com.iraychev.expenseanalyzer.mapper.BankConnectionMapper;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class BankConnectionService {
    private final GoCardlessIntegrationService goCardlessIntegrationService;
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankAccountMapper bankAccountMapper;
    private final BankConnectionRepository bankConnectionRepository;
    private final BankConnectionMapper bankConnectionMapper;
    private final TransactionMapper transactionMapper;
    private final TransactionCategorizationService categorizationService;

    /** GoCardless allows only 4 transaction fetches per account per day. */
    @Value("${sync.min-interval:6h}")
    private Duration minInterval = Duration.ofHours(6);

    /**
     * GoCardless requires references to be unique across the whole account (i.e. across all our users),
     * so the name the user typed is only used for display and a random suffix is sent instead.
     */
    private static final String REFERENCE_SEPARATOR = "_";
    private static final int REFERENCE_SUFFIX_LENGTH = 8;

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        String displayName = requestDto.getReference() == null ? "" : requestDto.getReference().trim();
        if (displayName.isEmpty()) {
            throw new ValidationException("reference", "Please provide a connection name");
        }
        requestDto.setReference(displayName + REFERENCE_SEPARATOR
                + UUID.randomUUID().toString().replace("-", "").substring(0, REFERENCE_SUFFIX_LENGTH));
        return goCardlessIntegrationService.createRequisition(requestDto);
    }

    static String toDisplayReference(String reference) {
        if (reference == null) {
            return null;
        }
        int idx = reference.lastIndexOf(REFERENCE_SEPARATOR);
        return idx > 0 && reference.length() - idx - 1 == REFERENCE_SUFFIX_LENGTH ? reference.substring(0, idx) : reference;
    }

    public List<BankAccountDto> listAccounts(String requisitionId) {
        return bankAccountRepository.findAllByBankConnection_RequisitionId(requisitionId)
                .stream()
                .map(bankAccountMapper::toDTO)
                .toList();
    }

    public BankConnectionDto updateBankConnection(String username, String requisitionId, boolean isNewConnection) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // A second "finalize" for the same requisition must not create a duplicate or burn quota again:
        // it is treated as a regular (throttled) sync of the existing connection.
        Optional<BankConnection> existing = bankConnectionRepository.findByRequisitionId(requisitionId);

        BankConnection bankConnection;
        List<BankAccount> bankAccounts;
        boolean createdNow = isNewConnection && existing.isEmpty();
        if (createdNow) {
            RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
            bankConnection = createBankConnection(requisition, user);
            bankAccounts = createBankAccounts(requisition, bankConnection);
        } else {
            bankConnection = existing.orElseThrow(() -> new ResourceNotFoundException("Bank connection not found"));
            bankAccounts = bankConnection.getAccounts();
        }

        Instant now = Instant.now();
        List<BankAccount> dueAccounts = bankAccounts.stream()
                .filter(account -> isSyncDue(account, now))
                .toList();

        int synced = 0;
        RateLimitException rateLimited = null;
        for (BankAccount account : dueAccounts) {
            try {
                BankAccountDto fetched = goCardlessIntegrationService
                        .updateBankAccountsWithFetchedTransactions(List.of(bankAccountMapper.toDTO(account)))
                        .get(0);
                mergeFetchedTransactions(account, fetched);
                account.setLastSyncedAt(now);
                synced++;
            } catch (RateLimitException e) {
                log.warn("GoCardless rate limit hit for account {}", account.getAccountId());
                rateLimited = e;
                break;
            }
        }
        if (rateLimited != null && synced == 0 && !createdNow) {
            throw rateLimited;
        }

        bankAccountRepository.saveAll(bankAccounts);
        bankConnection.setAccounts(bankAccounts);
        bankConnection = bankConnectionRepository.save(bankConnection);

        BankConnectionDto result = bankConnectionMapper.toDTO(bankConnection);
        boolean throttled = !bankAccounts.isEmpty() && dueAccounts.isEmpty();
        result.setThrottled(throttled);
        if (throttled) {
            result.setNextSyncAvailableAt(nextSyncAvailableAt(bankAccounts));
        }
        return result;
    }

    private boolean isSyncDue(BankAccount account, Instant now) {
        return account.getLastSyncedAt() == null
                || !account.getLastSyncedAt().plus(minInterval).isAfter(now);
    }

    private Instant nextSyncAvailableAt(List<BankAccount> accounts) {
        return accounts.stream()
                .map(BankAccount::getLastSyncedAt)
                .filter(Objects::nonNull)
                .map(last -> last.plus(minInterval))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    public void deleteBankConnection(String requisitionId) {
        BankConnection bankConnection = bankConnectionRepository.findByRequisitionId(requisitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank connection not found"));

        goCardlessIntegrationService.deleteRequisition(requisitionId);

        bankAccountRepository.deleteAll(bankConnection.getAccounts());
        bankConnectionRepository.delete(bankConnection);
    }

    private BankConnection createBankConnection(RequisitionDto requisition, User user) {
        BankConnection bankConnection = BankConnection.builder()
                .requisitionId(requisition.getId())
                .reference(toDisplayReference(requisition.getReference()))
                .institutionId(requisition.getInstitutionId())
                // Todo: Do I need institutionName?
                .institutionName(requisition.getInstitutionId())
                .user(user)
                .accounts(new ArrayList<>())
                .build();
        return bankConnectionRepository.save(bankConnection);
    }

    private List<BankAccount> createBankAccounts(RequisitionDto requisition, BankConnection bankConnection) {
        List<BankAccount> bankAccounts = new ArrayList<>();
        if (requisition.getAccounts() != null) {
            for (String accountId : requisition.getAccounts()) {
                BankAccount account = BankAccount.builder()
                        .accountId(accountId)
                        .iban(goCardlessIntegrationService.getBankAccountIban(accountId))
                        .transactions(new ArrayList<>())
                        .bankConnection(bankConnection)
                        .build();
                bankAccounts.add(account);
            }
        }
        return bankAccountRepository.saveAll(bankAccounts);
    }

    private void mergeFetchedTransactions(BankAccount account, BankAccountDto dto) {
        {
            if (dto.getTransactions() == null) {
                return;
            }

            // Keep only transactions that are not stored yet (by bank transaction id, with a multiset
            // fallback on date/amount/description for legacy rows without an id).
            List<Transaction> newTransactions = TransactionMerger
                    .selectNew(account.getTransactions(), dto.getTransactions()).stream()
                    .map(transactionDto -> {
                        Transaction transaction = transactionMapper.toEntity(transactionDto);
                        transaction.setBankAccount(account);
                        transaction.setOtherRetryCount(0);
                        return transaction;
                    })
                    .toList();

            categorizationService.categorize(newTransactions, account.getBankConnection());
            account.getTransactions().addAll(newTransactions);

            log.info("Added {} new transactions to account {}",
                    newTransactions.size(), account.getAccountId());
        }
    }
}