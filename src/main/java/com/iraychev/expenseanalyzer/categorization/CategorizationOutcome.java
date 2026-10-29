package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.domain.enums.CategorySource;
import com.iraychev.expenseanalyzer.domain.enums.CategoryStatus;

/**
 * Final result for one transaction.
 * <ul>
 *   <li>CATEGORIZED / UNCATEGORIZABLE: final answers.</li>
 *   <li>FAILED: the AI call failed (counts as a retry).</li>
 *   <li>PENDING: the AI was not asked at all (circuit breaker open); try again later without counting a retry.</li>
 * </ul>
 *
 * @param category the category key to store ("Other" when nothing better is known)
 * @param source   who decided; null when nobody did
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

    static CategorizationOutcome deferred() {
        return new CategorizationOutcome(Category.OTHER.key(), CategoryStatus.PENDING, null);
    }
}
