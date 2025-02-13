package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.dto.*;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.mapper.UserMapper;
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

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        return goCardlessIntegrationService.createRequisition(requestDto);
    }

    public List<BankAccountDto> listAccounts(String requisitionId) {
        RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
        List<BankAccountDto> accounts = new ArrayList<>();
        if (requisition.getAccounts() != null) {
            for (String accountId : requisition.getAccounts()) {
                accounts.add(BankAccountDto.builder()
                        .accountId(accountId)
                        .iban("Not yet known")
                        .transactions(new ArrayList<>())
                        .build());
            }
        }

        return accounts;
    }

    public List<TransactionDto> syncTransactions(String userEmail, String requisitionId) {
        RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
        List<BankAccountDto> accounts = listAccounts(requisitionId);
        UserDto user = userMapper.toDTO(userRepository.findByEmail(userEmail).orElseThrow(() -> new ResourceNotFoundException("User not found")));

        BankConnectionDto bankConnection = BankConnectionDto.builder()
                        .requisitionId(requisition.getId())
                        .reference(requisition.getReference())
                        .institutionId(requisition.getInstitutionId())
                        .institutionName("nz oshte")
                        .accounts(accounts)
                        .build();

        List<TransactionDto> transactions = goCardlessIntegrationService.fetchTransactions(accounts);
        transactions.forEach(transaction -> transaction.setBankConnection(bankConnection));
        bankConnection.setTransactions(transactions);

        user.getBankConnections().add(bankConnection);

        return transactionService.saveTransactions(transactions);
    }
}
