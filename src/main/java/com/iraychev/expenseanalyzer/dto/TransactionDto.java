package com.iraychev.expenseanalyzer.dto;

import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
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
    private LocalDateTime transactionDate;
    private String description;
    private TransactionType type;
}