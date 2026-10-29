package com.iraychev.expenseanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of PUT /api/v1/transactions/{id}/category. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryUpdateRequest {
    private String category;
    /** Also remember the choice for every transaction of this merchant (now and in the future). */
    private boolean applyToMerchant;
}
