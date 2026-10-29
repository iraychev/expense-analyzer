package com.iraychev.expenseanalyzer.domain.enums;

public enum CategoryStatus {
    /** Waiting to be categorized. */
    PENDING,
    CATEGORIZED,
    /** A technical failure (e.g. AI unavailable); retryable. */
    FAILED,
    /** Rules/model had no answer. */
    UNCATEGORIZABLE
}
