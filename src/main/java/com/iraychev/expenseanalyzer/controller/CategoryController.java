package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.categorization.Category;
import com.iraychev.expenseanalyzer.dto.CategoryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    @GetMapping
    public List<CategoryDto> listCategories() {
        return Category.all().stream().map(CategoryDto::of).toList();
    }
}
