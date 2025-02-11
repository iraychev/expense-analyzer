package com.iraychev.expenseanalyzer.dto;

import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionsResponse {
    private List<Transaction> transactions;
}
