package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountDto {
    private String iban;

    @JsonProperty("account_id")
    private String accountId;

    @JsonBackReference
    private BankConnectionDto bankConnection;

    @JsonManagedReference
    private List<TransactionDto> transactions;
}