package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.CategoryUpdateRequest;
import com.iraychev.expenseanalyzer.dto.TransactionDto;
import com.iraychev.expenseanalyzer.service.AiCategorizationService;
import com.iraychev.expenseanalyzer.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping("/categories")
    public List<String> getCategories() {
        return AiCategorizationService.VALID_CATEGORIES;
    }

    @PutMapping("/{id}/category")
    public TransactionDto updateCategory(@PathVariable Long id, @RequestBody CategoryUpdateRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return transactionService.updateCategory(username, id, request.category());
    }
}
