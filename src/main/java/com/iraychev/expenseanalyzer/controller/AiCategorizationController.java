package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.service.AiCategorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/api/v1/ai-categorization")
@RequiredArgsConstructor
public class AiCategorizationController {
    private final AiCategorizationService aiCategorizationService;

    @ResponseStatus(OK)
    @GetMapping
    public String categorizeTransaction(@RequestParam String remittanceInfo) {
        return aiCategorizationService.categorizeRemittance(remittanceInfo);
    }
}
