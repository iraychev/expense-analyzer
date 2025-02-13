package com.iraychev.expenseanalyzer.dto;

import com.iraychev.expenseanalyzer.domain.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConnectionDto {
    private String reference;
    private String institutionId;
    private String institutionName;
    private String requisitionId;
    private List<BankAccountDto> accounts = new ArrayList<>();
    private User user;
    private List<TransactionDto> transactions = new ArrayList<>();

}