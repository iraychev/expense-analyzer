package com.iraychev.expenseanalyzer.categorization;

import com.iraychev.expenseanalyzer.domain.enums.CategorySource;

/** A category chosen by one of the layers. */
public record Decision(Category category, CategorySource source) {
}
