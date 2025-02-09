package com.iraychev.expenseanalyzer.service;

import com.iraychev.expenseanalyzer.exception.BankConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {
    private final RestTemplate restTemplate;

    @Value("${gocardless.secret-id}")
    private String secretId;

    @Value("${gocardless.secret-key}")
    private String secretKey;

    private String accessToken;
    private Instant tokenExpiry;

    public synchronized String getValidAccessToken() {
        if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
            refreshToken();
        }
        return accessToken;
    }

    private void refreshToken() {
        String url = "https://bankaccountdata.gocardless.com/api/v2/token/new/";

        Map<String, String> request = Map.of(
                "secret_id", secretId,
                "secret_key", secretKey
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            this.accessToken = (String) response.getBody().get("access");
            long expiresIn = Long.parseLong(response.getBody().get("access_expires").toString());
            this.tokenExpiry = Instant.now().plusSeconds(expiresIn - 30);

        } catch (RestClientException e) {
            log.error("Failed to refresh GoCardless token", e);
            throw new BankConnectionException("Token refresh failed", e);
        }
    }
}