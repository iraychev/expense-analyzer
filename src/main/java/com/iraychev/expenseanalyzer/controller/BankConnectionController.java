package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.BankAccountDto;
import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
import com.iraychev.expenseanalyzer.service.BankConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/api/v1/requisitions")
@Slf4j
@RequiredArgsConstructor
public class BankConnectionController {

    private final BankConnectionService bankConnectionService;

    @ResponseStatus(CREATED)
    @PostMapping
    public RequisitionDto createRequisition(@RequestBody RequisitionRequestDto requisitionRequestDto) {
        return bankConnectionService.createRequisition(requisitionRequestDto);
    }

    @ResponseStatus(OK)
    @GetMapping("/{requisitionId}/accounts")
    public List<BankAccountDto> listAccounts(@PathVariable String requisitionId) {
        return bankConnectionService.listAccounts(requisitionId);
    }

    @ResponseStatus(OK)
    @DeleteMapping("/{requisitionId}")
    public void deleteBankConnection(@PathVariable String requisitionId) {
        bankConnectionService.deleteBankConnection(requisitionId);
    }
}