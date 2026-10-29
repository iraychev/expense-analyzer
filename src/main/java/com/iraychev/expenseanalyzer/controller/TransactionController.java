package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.CategoryUpdateRequest;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.service.TransactionCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionCategoryService categoryService;

    /** Changes the category of one of the caller's transactions (404 for anyone else's). */
    @PutMapping("/{id}/category")
    public TransactionDto updateCategory(@PathVariable Long id, @RequestBody CategoryUpdateRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        log.info("User {} changes category of transaction {} (applyToMerchant={})", username, id,
                request != null && request.isApplyToMerchant());
        return categoryService.updateCategory(username, id, request);
    }
}
