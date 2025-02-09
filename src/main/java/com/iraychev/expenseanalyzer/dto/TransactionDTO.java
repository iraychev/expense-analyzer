package com.iraychev.expenseanalyzer.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDTO {
    private Long id;
    private String accountId;
    private LocalDate bookingDate;
    private BigDecimal amount;
    private String currency;
    private String creditorName;
    private String category;
}