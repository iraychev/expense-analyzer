package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.service.BankConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bank-connections")
@RequiredArgsConstructor
public class BankConnectionController {
    private final BankConnectionService bankConnectionService;

    @PostMapping("/users/{userId}/connect")
    public ResponseEntity<Map<String, String>> initiateBankConnection(@PathVariable Long userId) {
        String authUrl = bankConnectionService.initiateBankConnection(userId);
        return ResponseEntity.ok(Map.of("authorizationUrl", authUrl));
    }

    @GetMapping("/callback")
    public ResponseEntity<String> handleCallback(
            @RequestParam String code,
            @RequestParam String state) {
        bankConnectionService.handleCallback(code, state);
        return ResponseEntity.ok("Bank account connected successfully");
    }
}