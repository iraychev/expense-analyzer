package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final CategoryService categoryService;

    /** Minimum time between two transaction fetches of the same account. */
    @Value("${bank.sync.min-interval:PT6H}")
    private Duration minSyncInterval = Duration.ofHours(6);

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
        RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BankConnection bankConnection;
        List<BankAccount> bankAccounts;
        if(isNewConnection) {
            bankConnection = createBankConnection(requisition, user);
            bankAccounts = createBankAccounts(requisition, bankConnection);
        }
        else {
            bankConnection = bankConnectionRepository.findByRequisitionId(requisitionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bank connection not found"));
            bankAccounts = bankConnection.getAccounts();
        }

        // The bank only allows a few transaction fetches per account per day, so recently synced accounts are skipped.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<BankAccount> dueAccounts = bankAccounts.stream()
                .filter(account -> isSyncDue(account, now))
                .toList();
        if (dueAccounts.size() < bankAccounts.size()) {
            log.info("Skipping {} account(s) synced within the last {}", bankAccounts.size() - dueAccounts.size(), minSyncInterval);
        }
        if (!dueAccounts.isEmpty()) {
            List<BankAccountDto> updatedAccounts = goCardlessIntegrationService.updateBankAccountsWithFetchedTransactions(dueAccounts.stream()
                    .map(bankAccountMapper::toDTO)
                    .toList());
            updateBankAccountsWithTransactions(dueAccounts, updatedAccounts, now);
        }
        bankConnection.setAccounts(bankAccounts);
        bankConnection = bankConnectionRepository.save(bankConnection);

        return bankConnectionMapper.toDTO(bankConnection);
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

    private boolean isSyncDue(BankAccount account, LocalDateTime now) {
        return account.getLastSyncedAt() == null || !account.getLastSyncedAt().plus(minSyncInterval).isAfter(now);
    }

    private void updateBankAccountsWithTransactions(List<BankAccount> bankAccounts, List<BankAccountDto> updatedAccounts,
                                                    LocalDateTime now) {
        for (int i = 0; i < bankAccounts.size(); i++) {
            BankAccount account = bankAccounts.get(i);
            List<Transaction> newTransactions = findNewTransactions(account, updatedAccounts.get(i).getTransactions());

            List<String> categories = categoryService.categorizeTransactions(
                    newTransactions.stream().map(Transaction::getDescription).toList());
            for (int t = 0; t < newTransactions.size(); t++) {
                Transaction transaction = newTransactions.get(t);
                transaction.setCategory(categories.get(t));
                transaction.setOtherRetryCount(0);
                transaction.setOtherNextRetryAt("Other".equalsIgnoreCase(categories.get(t)) ? now.plusDays(1) : null);
            }

            account.getTransactions().addAll(newTransactions);
            account.setLastSyncedAt(now);

            log.info("Added {} new transactions to account {}",
                    newTransactions.size(), account.getAccountId());
        }
        bankAccountRepository.saveAll(bankAccounts);
    }

    /**
     * Returns the fetched transactions the account does not have yet. A transaction is known if its bank id is
     * stored. Rows synced before ids were stored are matched by (date, amount, description), one stored row per
     * fetched one so that repeated identical purchases are not collapsed, and get their id filled in.
     */
    List<Transaction> findNewTransactions(BankAccount account, List<TransactionDto> fetched) {
        Set<String> knownIds = new HashSet<>();
        Map<List<Object>, Deque<Transaction>> withoutId = new HashMap<>();
        for (Transaction existing : account.getTransactions()) {
            if (existing.getExternalId() != null) {
                knownIds.add(existing.getExternalId());
            } else {
                withoutId.computeIfAbsent(matchKey(existing.getTransactionDate(), existing.getAmount(), existing.getDescription()),
                        k -> new ArrayDeque<>()).add(existing);
            }
        }

        List<Transaction> newTransactions = new ArrayList<>();
        for (TransactionDto dto : fetched) {
            String externalId = dto.getExternalId();
            if (externalId != null && knownIds.contains(externalId)) {
                continue;
            }
            Deque<Transaction> candidates = withoutId.get(matchKey(dto.getTransactionDate(), dto.getAmount(), dto.getDescription()));
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
            Transaction transaction = transactionMapper.toEntity(dto);
            transaction.setBankAccount(account);
            newTransactions.add(transaction);
        }
        return newTransactions;
    }

    private static List<Object> matchKey(LocalDateTime date, BigDecimal amount, String description) {
        return Arrays.asList(date, amount == null ? null : amount.stripTrailingZeros(), description);
    }
}