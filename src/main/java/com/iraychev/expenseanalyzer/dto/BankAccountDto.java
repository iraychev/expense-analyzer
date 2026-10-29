package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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

    private Instant lastSyncedAt;

    private List<TransactionDto> transactions;
}