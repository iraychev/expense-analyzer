package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountDto {
    private Long id;
    private String iban;

    @JsonProperty("account_id")
    private String accountId;

    private Long bankConnectionId;

    private LocalDateTime lastSyncedAt;

    private List<TransactionDto> transactions;
}