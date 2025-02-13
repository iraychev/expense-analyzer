package com.iraychev.expenseanalyzer.dto;

import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDto {
    private BigDecimal amount;
    private String currency;
    private LocalDateTime valuenDate;
    private LocalDateTime transactionDate;
    private String description;
    private TransactionType type;
    private BankConnectionDto bankConnection;
}