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

    public BankConnectionDto syncTransactions(String userEmail, String requisitionId) {
        RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BankConnection bankConnection = BankConnection.builder()
                .requisitionId(requisition.getId())
                .reference(requisition.getReference())
                .institutionId(requisition.getInstitutionId())
                .institutionName("nz oshte")
                .user(user)
                .accounts(new ArrayList<>())
                .build();

        bankConnection = bankConnectionRepository.save(bankConnection);

        List<BankAccount> bankAccounts = new ArrayList<>();
        if (requisition.getAccounts() != null) {
            for (String accountId : requisition.getAccounts()) {
                BankAccount account = BankAccount.builder()
                        .accountId(accountId)
                        .iban("Not yet known")
                        .transactions(new ArrayList<>())
                        .bankConnection(bankConnection)
                        .build();
                bankAccounts.add(account);
            }
        }

        bankAccounts = bankAccountRepository.saveAll(bankAccounts);

        bankConnection.setAccounts(bankAccounts);
        bankConnection = bankConnectionRepository.save(bankConnection);

        List<BankAccountDto> accountDtos = bankAccounts.stream()
                .map(bankAccountMapper::toDTO)
                .toList();
        List<BankAccountDto> updatedAccounts = goCardlessIntegrationService.fetchTransactions(accountDtos);
        BankConnection finalBankConnection = bankConnection;
        List<BankAccount> updatedBankAccounts = updatedAccounts.stream()
                .map(dto -> {
                    BankAccount account = bankAccountMapper.toEntity(dto);
                    account.setBankConnection(finalBankConnection);
                    account.setTransactions(dto.getTransactions().stream()
                            .map(transactionDto -> {
                                Transaction transaction = transactionMapper.toEntity(transactionDto);
                                transaction.setBankAccount(account);
                                return transaction;
                            })
                            .collect(Collectors.toList()));
                    return account;
                })
                .toList();
        updatedBankAccounts = bankAccountRepository.saveAll(updatedBankAccounts);

        bankConnection.setAccounts(updatedBankAccounts);
        bankConnection = bankConnectionRepository.save(bankConnection);
        return bankConnectionMapper.toDTO(bankConnection);
    }
}