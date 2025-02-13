package com.iraychev.expenseanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConnectionDto {
    private String reference;
    private String institutionId;
    private String institutionName;
    private String requisitionId;
    private List<BankAccountDTO> accounts = new ArrayList<>();
    private User user;
    private List<TransactionDTO> transactions = new ArrayList<>();

}