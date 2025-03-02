package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.*;
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
        // Todo: Consider persisting requisitions in the database instead of fetching them every time
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

    private BankConnection createBankConnection(RequisitionDto requisition, User user) {
        BankConnection bankConnection = BankConnection.builder()
                .requisitionId(requisition.getId())
                .reference(requisition.getReference())
                .institutionId(requisition.getInstitutionId())
                // Todo: Do I need institutionName?
                .institutionName("nz oshte")
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
                        // Todo: Iban can be fetched from the API
                        .iban("Not yet known")
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
            account.setTransactions(dto.getTransactions().stream()
                    .map(transactionDto -> {
                        Transaction transaction = transactionMapper.toEntity(transactionDto);
                        transaction.setBankAccount(account);
                        return transaction;
                    })
                    .collect(Collectors.toList()));
        }
        bankAccountRepository.saveAll(bankAccounts);
    }
}