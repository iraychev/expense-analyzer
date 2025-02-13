package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.BankConnectionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.service.BankConnectionService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bank-accounts")
@Slf4j
@RequiredArgsConstructor
public class BankConnectionController {

    private final BankConnectionService bankConnectionService;

    // Step 4: Create a requisition (build a link)
    @PostMapping("/requisitions")
    public ResponseEntity<RequisitionDto> createRequisition(@RequestBody RequisitionRequestDto requisitionRequestDto) {
        RequisitionDto requisition = bankConnectionService.createRequisition(requisitionRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(requisition);
    }

    // Step 5: List accounts from a requisition
    @GetMapping("/requisitions/{requisitionId}/accounts")
    public ResponseEntity<List<BankConnectionDto>> listAccounts(@PathVariable String requisitionId) {
        List<BankConnectionDto> accounts = bankConnectionService.listAccounts(requisitionId);
        return ResponseEntity.ok(accounts);
    }

    // Step 6: Sync transactions for a bank account
    @PostMapping("/{BankConnectionId}/sync")
    public ResponseEntity<List<TransactionDto>> syncTransactions(@PathVariable Long BankConnectionId,
                                                                 @RequestHeader("Authorization") String accessToken) {
        List<TransactionDto> transactions = bankConnectionService.syncTransactions(BankConnectionId, accessToken);
        return ResponseEntity.ok(transactions);
    }
}
