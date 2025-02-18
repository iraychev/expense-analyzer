package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.*;
import com.iraychev.expenseanalyzer.service.BankConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @PostMapping("/requisitions")
    public ResponseEntity<RequisitionDto> createRequisition(@RequestBody RequisitionRequestDto requisitionRequestDto) {
        RequisitionDto requisition = bankConnectionService.createRequisition(requisitionRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(requisition);
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/requisitions/{requisitionId}/accounts")
    public List<BankAccountDto> listAccounts(@PathVariable String requisitionId) {
        return bankConnectionService.listAccounts(requisitionId);
    }
}