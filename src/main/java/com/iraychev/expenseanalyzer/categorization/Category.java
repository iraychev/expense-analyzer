package com.iraychev.expenseanalyzer.categorization;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The closed set of categories. The key is stable, i18n-free and (for backward compatibility with stored data and
 * the deployed UI) equal to the English label of the original categories.
 */
public enum Category {
    SUPERMARKETS("Supermarkets", Kind.EXPENSE),
    FINANCIAL_SERVICES("Financial Services", Kind.EXPENSE),
    SHOPPING("Shopping", Kind.EXPENSE),
    RESTAURANTS("Restaurants and bars", Kind.EXPENSE),
    ENTERTAINMENT("Entertainment and Sport", Kind.EXPENSE),
    TRAVEL("Traveling and Vacation", Kind.EXPENSE),
    HEALTH("Health and Beauty", Kind.EXPENSE),
    FEES("Fees", Kind.EXPENSE),
    OTHER("Other", Kind.EXPENSE),
    SALARY("Salary", Kind.INCOME),
    OTHER_INCOME("Other income", Kind.INCOME),
    TRANSFER_IN("Transfer in", Kind.TRANSFER),
    TRANSFER_OUT("Transfer out", Kind.TRANSFER);

    public enum Kind { EXPENSE, INCOME, TRANSFER }

    private final String key;
    private final Kind kind;

    Category(String key, Kind kind) {
        this.key = key;
        this.kind = kind;
    }

    public String key() {
        return key;
    }

    public String label() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    public static List<Category> all() {
        return List.of(values());
    }

    public static Optional<Category> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String trimmed = key.trim().replace("&", "and");
        return Arrays.stream(values()).filter(c -> c.key.equalsIgnoreCase(trimmed)).findFirst();
    }
}
