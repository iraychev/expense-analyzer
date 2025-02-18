package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.User;
import com.iraychev.expenseanalyzer.dto.*;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.mapper.BankAccountMapper;
import com.iraychev.expenseanalyzer.mapper.BankConnectionMapper;
import com.iraychev.expenseanalyzer.mapper.UserMapper;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import com.iraychev.expenseanalyzer.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class BankConnectionService {
    private final GoCardlessIntegrationService goCardlessIntegrationService;
    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final BankAccountRepository bankAccountRepository;
    private final BankAccountMapper bankAccountMapper;
    private final BankConnectionRepository bankConnectionRepository;
    private final BankConnectionMapper bankConnectionMapper;

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        return goCardlessIntegrationService.createRequisition(requestDto);
    }

    public List<BankAccountDto> listAccounts(String requisitionId) {
        return bankAccountRepository.findAllByBankConnection_RequisitionId(requisitionId)
                .stream()
                .map(bankAccountMapper::toDTO)
                .toList();
    }

    public BankConnectionDto syncTransactions(String userEmail, String requisitionId) {
        RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Create and save BankConnection first
        BankConnection bankConnection = BankConnection.builder()
                .requisitionId(requisition.getId())
                .reference(requisition.getReference())
                .institutionId(requisition.getInstitutionId())
                .institutionName("nz oshte")
                .user(user)
                .accounts(new ArrayList<>())  // Empty list initially
                .build();

        bankConnection = bankConnectionRepository.save(bankConnection);  // Save it first

        // Now create accounts that reference this saved BankConnection
        List<BankAccount> bankAccounts = new ArrayList<>();
        if (requisition.getAccounts() != null) {
            for (String accountId : requisition.getAccounts()) {
                BankAccount account = BankAccount.builder()
                        .accountId(accountId)
                        .iban("Not yet known")
                        .transactions(new ArrayList<>())
                        .bankConnection(bankConnection)  // Reference the saved connection
                        .build();
                bankAccounts.add(account);
            }
        }

        // Save all accounts
        bankAccounts = bankAccountRepository.saveAll(bankAccounts);

        // Update the connection's accounts list
        bankConnection.setAccounts(bankAccounts);
        bankConnection = bankConnectionRepository.save(bankConnection);

        // Convert to DTOs and continue with transactions
        List<BankAccountDto> accountDtos = bankAccounts.stream()
                .map(bankAccountMapper::toDTO)
                .toList();
        List<BankAccountDto> updatedAccounts = goCardlessIntegrationService.fetchTransactions(accountDtos);
        bankConnection.setAccounts(updatedAccounts.stream().map(bankAccountMapper::toEntity).toList());
        bankConnectionRepository.save(bankConnection);
        return bankConnectionMapper.toDTO(bankConnection);
    }
}
