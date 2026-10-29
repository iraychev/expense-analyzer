package com.iraychev.expenseanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;
import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDto {
    private Long id;
    private String externalId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime valueDate;
    private LocalDateTime transactionDate;
    private String category;
    private String merchant;
    private CategoryStatus categoryStatus;
    private CategorySource categorySource;

    /** Parser inputs: stored with the transaction but not exposed to clients. */
    @JsonIgnore
    private String counterpartyName;
    @JsonIgnore
    private String bankTypeCode;
    private String description;
    private TransactionType type;

    private Long bankAccountId;
}