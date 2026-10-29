package com.iraychev.expenseanalyzer.categorization;

/** Published when a sync stored new transactions that wait to be categorized. */
public record TransactionsSyncedEvent(int newTransactions) {
}
