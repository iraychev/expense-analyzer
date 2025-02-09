package com.iraychev.expenseanalyzer.service;

import com.gocardless.GoCardlessClient;
import com.gocardless.GoCardlessException;
import com.gocardless.resources.CustomerBankAccount;
import com.iraychev.expenseanalyzer.entity.BankAccount;
import com.iraychev.expenseanalyzer.entity.User;
import com.iraychev.expenseanalyzer.exception.BankConnectionException;
import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
import com.iraychev.expenseanalyzer.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankConnectionService {
    private final GoCardlessClient goCardlessClient;
    private final BankAccountRepository bankAccountRepository;
    private final UserService userService;

    @Value("${gocardless.redirect-uri}")
    private String redirectUri;

    @Value("${gocardless.customer-bank-account.currency}")
    private String currency;

    @Value("${gocardless.customer-bank-account.country-code}")
    private String countryCode;

    // Simulate generating an authorization URL (normally a requisition link)
    @Transactional
    public String initiateBankConnection(Long userId) {
        log.info("Initiating bank connection for user {}", userId);
        return "https://gocardless.com/authorize?state=" + userId + "&redirect_uri=" + redirectUri;
    }

    // Handles the callback by exchanging the authorization code for bank account details.
    @Transactional
    public void handleCallback(String code, String state) {
        Long userId;
        try {
            userId = Long.parseLong(state);
        } catch (NumberFormatException e) {
            throw new BankConnectionException("Invalid state parameter", e);
        }
        User user = userService.findUserById(userId);

        CustomerBankAccount customerBankAccount = goCardlessClient.customerBankAccounts()
                .create()
                .withAccountHolderName("John Doe")     // Replace with real data from callback
                .withAccountNumber("00012345")           // Replace with real bank account details
                .withBranchCode("123456")
                .withCountryCode(countryCode)
                .withCurrency(currency)
                .execute();

        // Map and save the bank account into your local database.
        BankAccount bankAccount = BankAccount.builder()
                .accountId(customerBankAccount.getId())
                .bankName("Demo Bank") // Replace with bank name from response if available.
                .status("ACTIVE")
                .user(user)
                .connectedAt(LocalDateTime.now())
                .build();

        bankAccountRepository.save(bankAccount);
        log.info("Bank account connected successfully for user {}", userId);
    }

    @Transactional
    public void disconnectBankAccount(Long userId, String accountId) {
        BankAccount bankAccount = bankAccountRepository.findByAccountIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found"));
        try {
            goCardlessClient.customerBankAccounts()
                    .disable(accountId)
                    .execute();
            bankAccount.setStatus("DISABLED");
            bankAccountRepository.save(bankAccount);
            log.info("Successfully disabled bank account {} for user {}", accountId, userId);
        } catch (Exception e) {
            log.error("Failed to disable bank account {} for user {}", accountId, userId, e);
            throw new BankConnectionException("Failed to disable bank account", e);
        }
    }

    @Transactional(readOnly = true)
    public List<BankAccount> getUserBankAccounts(Long userId) {
        return bankAccountRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public BankAccount getBankAccount(Long userId, String accountId) {
        return bankAccountRepository.findByAccountIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found"));
    }
}