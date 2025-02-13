package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankConnection;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.repository.BankConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
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
    private final BankConnectionRepository bankConnectionRepository;
    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        return goCardlessIntegrationService.createRequisition(requestDto);
    }

    public List<BankAccountDto> listAccounts(String requisitionId) {
        RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
        List<BankAccountDto> accounts = new ArrayList<>();
        if (requisition.getAccounts() != null) {
            for (String accountId : requisition.getAccounts()) {
                dtos.add(BankAccountDto.builder()
                        .accountId(accountId)
                        .iban("Not yet known")
                        .transactions(new ArrayList<TransactionDto())
                        .build());
            }
        }

        return dtos;
    }

    public List<TransactionDto> syncTransactions(String userEmail, String requisitionId) {
        RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
        List<BankAccount> accounts = listAccounts(requisitionId)''
        UserDto user= userService.getByEmail(userEMail);

        BankConnectionDto bankConnection = BankConnectionDto.builder()
                        .requisitionId(requisition.getId())
                        .reference(requisition.getReference()
                        .institutionId(requisition.getInstitutionID())
                        .institutionName("nz oshte")
                        .accounts(accounts)
                        .build()) 
        List<TransactionDto> transactions = goCardlessIntegrationService.fetchTransactions(accounts);
        transactions.foreach(transaction -> transaction.bankConnection = bankConnection);
        bankConnection.setTransactions(transactions);

        List<Transaction> savedTransactions = transactionService.saveTransactions(transactions);
        user.bankConnections.add(bankConnection);
        return savedTransactions.stream().map(transactionMapper::toDTO).toList();
    }
}
