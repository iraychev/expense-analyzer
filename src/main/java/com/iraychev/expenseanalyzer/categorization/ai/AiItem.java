package com.iraychev.expenseanalyzer.categorization.ai;

import com.iraychev.expenseanalyzer.categorization.TransactionDirection;

/**
 * One thing to categorize.
 *
 * @param id        caller supplied id echoed back in the answer
 * @param text      redacted text
 * @param direction INCOME or EXPENSE (transfers never reach the AI)
 */
public record AiItem(String id, String text, TransactionDirection direction) {
}
