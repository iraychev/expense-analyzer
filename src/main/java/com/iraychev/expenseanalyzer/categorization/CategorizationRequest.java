package com.iraychev.expenseanalyzer.categorization;

/**
 * One transaction to categorize.
 *
 * @param userId owner of the transaction (for per-user overrides); may be null
 */
public record CategorizationRequest(Long userId, ParsedTransaction parsed) {
}
