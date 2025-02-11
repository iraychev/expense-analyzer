package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.service.BankAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bank-accounts")
@Slf4j
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    // Step 4: Create a requisition (build a link)
    @PostMapping("/requisitions")
    public ResponseEntity<RequisitionDto> createRequisition(@RequestBody RequisitionRequestDto requisitionRequestDto) {
        RequisitionDto requisition = bankAccountService.createRequisition(requisitionRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(requisition);
    }

    // Step 5: List accounts from a requisition
    @GetMapping("/requisitions/{requisitionId}/accounts")
    public ResponseEntity<List<BankAccountDto>> listAccounts(@PathVariable String requisitionId) {
        List<BankAccountDto> accounts = bankAccountService.listAccounts(requisitionId);
        return ResponseEntity.ok(accounts);
    }

    // Step 6: Sync transactions for a bank account
    @PostMapping("/{bankAccountId}/sync")
    public ResponseEntity<List<TransactionDto>> syncTransactions(@PathVariable Long bankAccountId,
                                                                 @RequestHeader("Authorization") String accessToken) {
        List<TransactionDto> transactions = bankAccountService.syncTransactions(bankAccountId, accessToken);
        return ResponseEntity.ok(transactions);
    }
}
