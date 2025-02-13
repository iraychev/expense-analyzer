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

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        return goCardlessIntegrationService.createRequisition(requestDto);
    }

    public List<BankConnectionDto> listAccounts(String requisitionId) {
        RequisitionDto requisition = goCardlessIntegrationService.getRequisition(requisitionId);
        List<BankConnectionDto> dtos = new ArrayList<>();
        if (requisition.getAccounts() != null) {
            for (String accountId : requisition.getAccounts()) {
                dtos.add(BankConnectionDto.builder()
                        .externalAccountId(accountId)
                        .accountName("Account " + accountId)
                        .institutionId("MappedInstitutionId")
                        .institutionName("MappedInstitutionName")
                        .build());
            }
        }
        return dtos;
    }

    public List<TransactionDto> syncTransactions(Long BankConnectionId, String accessTokenHeader) {
        BankConnection BankConnection = bankConnectionRepository.findById(BankConnectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found"));
        List<Transaction> transactions = goCardlessIntegrationService.fetchTransactions(
                BankConnection.getAccountId(), accessTokenHeader);
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
