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
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankConnectionService {
    private final GoCardlessClient goCardlessClient;
    private final BankAccountRepository bankAccountRepository;
    private final UserService userService;
    private final RestTemplate restTemplate;

    @Value("${gocardless.redirect-uri}")
    private String redirectUri;

    @Value("${gocardless.agreement-id}")
    private String agreementId;

    @Value("${gocardless.access-token}")
    private String accessToken;
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

    @Transactional
    public String initiateBankConnection(Long userId, String institutionId) {
        log.info("Initiating bank connection for user {} with institution {}", userId, institutionId);

        String url = "https://bankaccountdata.gocardless.com/api/v2/requisitions/";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken); // use the access token from application.yaml
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Prepare the request payload dynamically using the provided institutionId.
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("redirect", redirectUri);
        requestBody.put("institution_id", institutionId); // dynamic institution id from the call
        requestBody.put("reference", String.valueOf(userId)); // you can use userId or any unique reference
        requestBody.put("agreement", agreementId);
        requestBody.put("user_language", countryCode);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // Call the real GoCardless endpoint to create a requisition.
        ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);

        if (responseEntity.getStatusCode() == HttpStatus.CREATED || responseEntity.getStatusCode() == HttpStatus.OK) {
            Map responseMap = responseEntity.getBody();
            String authorizationUrl = (String) responseMap.get("link");
            // Optionally, you can save the requisition ID (from responseMap.get("id")) for later use.
            return authorizationUrl;
        } else {
            throw new BankConnectionException("Failed to create bank requisition, status code: " + responseEntity.getStatusCode(), new RuntimeException());
        }
    }

    @Transactional
    public void handleCallback(String code, String state) {
        Long userId;
        try {
            userId = Long.parseLong(state);
        } catch (NumberFormatException e) {
            throw new BankConnectionException("Invalid state parameter", e);
        }
        User user = userService.findUserById(userId);

        // In a real implementation, exchange the authorization code for bank account details.
        // Here we simulate by creating a bank account with dummy data.
        CustomerBankAccount customerBankAccount = goCardlessClient.customerBankAccounts()
                .create()
                .withAccountHolderName("John Doe")    // Replace with actual data from GoCardless response
                .withAccountNumber("00012345")          // Replace with real bank account details
                .withBranchCode("123456")
                .withCountryCode("GB")
                .withCurrency("GBP")
                .execute();

        BankAccount bankAccount = BankAccount.builder()
                .accountId(customerBankAccount.getId())
                .bankName("Real Bank Name")  // Ideally, map this from the response
                .status("ACTIVE")
                .user(user)
                .connectedAt(LocalDateTime.now())
                .build();

        bankAccountRepository.save(bankAccount);
        log.info("Bank account connected successfully for user {}", userId);
    }
}