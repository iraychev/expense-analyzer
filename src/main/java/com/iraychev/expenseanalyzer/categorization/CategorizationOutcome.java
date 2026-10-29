package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;

/**
 * Final result for one transaction.
 *
 * @param category the stored category key ("Other" when nothing better is known)
 * @param source   who decided; null when nobody did (FAILED)
 */
public record CategorizationOutcome(String category, CategoryStatus status, CategorySource source) {

    static CategorizationOutcome categorized(Decision decision) {
        return new CategorizationOutcome(decision.category().key(), CategoryStatus.CATEGORIZED, decision.source());
    }

    static CategorizationOutcome uncategorizable(CategorySource source) {
        return new CategorizationOutcome(Category.OTHER.key(), CategoryStatus.UNCATEGORIZABLE, source);
    }

    static CategorizationOutcome failed() {
        return new CategorizationOutcome(Category.OTHER.key(), CategoryStatus.FAILED, null);
    }
}
