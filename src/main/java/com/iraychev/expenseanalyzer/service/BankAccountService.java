package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.domain.entity.BankAccount;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
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
public class BankAccountService {

    private final GoCardlessIntegrationService goCardlessIntegrationService;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionService transactionService;

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        return goCardlessIntegrationService.createRequisition(requestDto);
    }

    public List<BankAccountDto> listAccounts(String requisitionId) {
        RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
        List<BankAccountDto> dtos = new ArrayList<>();
        if (requisition.getAccounts() != null) {
            for (String accountId : requisition.getAccounts()) {
                dtos.add(BankAccountDto.builder()
                        .externalAccountId(accountId)
                        .accountName("Account " + accountId) // Replace with actual mapping if available
                        .institutionId("MappedInstitutionId")
                        .institutionName("MappedInstitutionName")
                        .build());
            }
        }
        return dtos;
    }

    public List<TransactionDto> syncTransactions(Long bankAccountId, String accessTokenHeader) {
        BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found"));
        List<Transaction> transactions = goCardlessIntegrationService.fetchTransactions(
                bankAccount.getAccountId(), accessTokenHeader);
        List<Transaction> savedTransactions = transactionService.saveTransactions(transactions);
        return savedTransactions.stream().map(txn ->
                TransactionDto.builder()
                        .id(txn.getId())
                        .amount(txn.getAmount())
                        .currency(txn.getCurrency())
                        .transactionDate(txn.getTransactionDate())
                        .description(txn.getDescription())
                        .type(txn.getType())
                        .build()
        ).collect(Collectors.toList());
    }
}
