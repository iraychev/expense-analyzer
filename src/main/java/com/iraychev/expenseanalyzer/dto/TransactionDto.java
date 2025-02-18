package com.iraychev.expenseanalyzer.dto;

import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDto {
    private Long id;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime valueDate;
    private LocalDateTime transactionDate;
    private String description;
    private TransactionType type;

    private Long bankAccountId;
}