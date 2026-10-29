package com.iraychev.expenseanalyzer.config;

import com.iraychev.expenseanalyzer.dto.TokenResponse;
import com.iraychev.expenseanalyzer.exception.BankIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Keeps a valid GoCardless access token without manual intervention.
 *
 * <p>Access tokens are short lived (~24h) and are renewed with the refresh token. The refresh
 * token expires after ~30 days and cannot itself be renewed, so once it is gone a completely new
 * token pair is requested with the API secret id/key.
 */
@Slf4j
@Component
public class GoCardlessTokenProvider {

    /** Renew slightly early so a token never expires mid-request. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);

    private final String apiBaseUrl;
    private final String secretId;
    private final String secretKey;

    private String accessToken;
    private Instant accessExpiry = Instant.EPOCH;
    private String refreshToken;
    private Instant refreshExpiry = Instant.EPOCH;

    public GoCardlessTokenProvider(@Value("${gocardless.api.base-url}") String apiBaseUrl,
                                   @Value("${gocardless.api.secret-id}") String secretId,
                                   @Value("${gocardless.api.secret-key}") String secretKey) {
        this.apiBaseUrl = apiBaseUrl;
        this.secretId = secretId;
        this.secretKey = secretKey;
    }

    public synchronized String getAccessToken() {
        if (isValid(accessToken, accessExpiry)) {
            return accessToken;
        }

        if (isValid(refreshToken, refreshExpiry)) {
            try {
                refreshAccessToken();
                return accessToken;
            } catch (WebClientResponseException e) {
                log.warn("Refreshing GoCardless access token failed ({}), requesting a new token pair",
                        e.getStatusCode());
            }
        }

        requestNewTokenPair();
        return accessToken;
    }

    private boolean isValid(String token, Instant expiry) {
        return token != null && Instant.now().isBefore(expiry.minus(EXPIRY_MARGIN));
    }

    private void refreshAccessToken() {
        log.info("Refreshing GoCardless access token...");
        TokenResponse response = post("/token/refresh/", Collections.singletonMap("refresh", refreshToken));
        storeAccess(response);
    }

    private void requestNewTokenPair() {
        log.info("Requesting new GoCardless token pair...");
        TokenResponse response = post("/token/new/", Map.of("secret_id", secretId, "secret_key", secretKey));
        storeAccess(response);
        if (response.getRefresh() == null || response.getRefreshExpires() == null) {
            throw new BankIntegrationException("GoCardless did not return a refresh token");
        }
        this.refreshToken = response.getRefresh();
        this.refreshExpiry = Instant.now().plusSeconds(response.getRefreshExpires());
        log.info("New refresh token expires at: {}", refreshExpiry);
    }

    private void storeAccess(TokenResponse response) {
        if (response == null || response.getAccess() == null || response.getAccessExpires() == null) {
            throw new BankIntegrationException("GoCardless returned an invalid token response");
        }
        this.accessToken = response.getAccess();
        this.accessExpiry = Instant.now().plusSeconds(response.getAccessExpires());
        log.info("New access token expires at: {}", accessExpiry);
    }

    /** Plain client on purpose: token calls must not go through the logging/auth filters. */
    private TokenResponse post(String uri, Map<String, String> body) {
        try {
            return WebClient.create(apiBaseUrl)
                    .post()
                    .uri(uri)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            if (uri.equals("/token/new/")) {
                // Bad secrets: nothing left to fall back to.
                throw new BankIntegrationException("GoCardless rejected the API secret id/key: " + e.getStatusCode());
            }
            throw e;
        }
    }
}
