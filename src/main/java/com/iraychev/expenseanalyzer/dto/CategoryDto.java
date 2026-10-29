package com.iraychev.expenseanalyzer.dto;

import com.iraychev.expenseanalyzer.categorization.Category;

/** A selectable category: {@code kind} is EXPENSE, INCOME or TRANSFER. */
public record CategoryDto(String key, String label, String kind) {

    public static CategoryDto of(Category category) {
        return new CategoryDto(category.key(), category.label(), category.kind().name());
    }
}
