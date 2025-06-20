package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.mapper.BankAccountMapper;
import com.iraychev.expenseanalyzer.mapper.BankConnectionMapper;
import com.iraychev.expenseanalyzer.mapper.TransactionMapper;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        return goCardlessIntegrationService.createRequisition(requestDto);
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

        List<BankAccountDto> updatedAccounts = goCardlessIntegrationService.updateBankAccountsWithFetchedTransactions(bankAccounts.stream()
                .map(bankAccountMapper::toDTO)
                .toList());

        updateBankAccountsWithTransactions(bankAccounts, updatedAccounts);
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
                .reference(requisition.getReference())
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

    private void updateBankAccountsWithTransactions(List<BankAccount> bankAccounts, List<BankAccountDto> updatedAccounts) {
        for (int i = 0; i < bankAccounts.size(); i++) {
            BankAccount account = bankAccounts.get(i);
            BankAccountDto dto = updatedAccounts.get(i);

            Set<String> existingDescriptions = account.getTransactions().stream()
                    .map(Transaction::getDescription)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Filter out transactions that already exist (based on description)
            List<Transaction> newTransactions = dto.getTransactions().stream()
                    .filter(transactionDto -> transactionDto.getDescription() != null
                            && !existingDescriptions.contains(transactionDto.getDescription()))
                    .map(transactionDto -> {
                        Transaction transaction = transactionMapper.toEntity(transactionDto);
                        transaction.setBankAccount(account);
                        return transaction;
                    })
                    .toList();

            account.getTransactions().addAll(newTransactions);

            log.info("Added {} new transactions to account {}",
                    newTransactions.size(), account.getAccountId());
        }
        bankAccountRepository.saveAll(bankAccounts);
    }
}