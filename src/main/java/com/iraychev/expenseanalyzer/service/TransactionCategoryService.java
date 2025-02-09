package com.iraychev.expenseanalyzer.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TransactionCategoryService {
    private final Map<Pattern, String> categoryPatterns = new HashMap<>();

    @PostConstruct
    public void initializeCategoryPatterns() {
        categoryPatterns.put(Pattern.compile("(?i)coffee|starbucks|costa"), "Coffee");
        categoryPatterns.put(Pattern.compile("(?i)restaurant|takeaway|food|uber\\s*eats"), "Food & Dining");
        categoryPatterns.put(Pattern.compile("(?i)amazon|shopping|retail"), "Shopping");
        categoryPatterns.put(Pattern.compile("(?i)transport|uber|lyft|taxi"), "Transportation");
        categoryPatterns.put(Pattern.compile("(?i)netflix|spotify|subscription"), "Entertainment");
    }

    public String categorizeTransaction(String description) {
        if (description == null) return "Uncategorized";
        return categoryPatterns.entrySet().stream()
                .filter(entry -> entry.getKey().matcher(description).find())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("Uncategorized");
    }
}