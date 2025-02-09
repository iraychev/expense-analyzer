package com.iraychev.expenseanalyzer.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private List<TransactionDTO> transactions;
    private List<BankAccountDTO> bankAccounts;
}