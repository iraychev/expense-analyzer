package com.iraychev.expenseanalyzer.config;

import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${gocardless.api.base-url}")
    private String apiBaseUrl;

    @Value("${gocardless.api.refresh-token}")
    private String refreshToken;

    @Value("${gocardless.api.access-token}")
    private String accessToken;

    @Value("${gocardless.api.access-token-expiry}")
    private Long accessTokenExpiry;

    private static final String BEARER = "Bearer ";

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .baseUrl(apiBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .filter(logRequest())
                .filter(tokenExchangeFilter())  // Filter to attach token
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("Request: ")
                    .append(clientRequest.method())
                    .append(" ")
                    .append(clientRequest.url())
                    .append("\n");

            clientRequest.headers().forEach((name, values) -> {
                values.forEach(value -> logMessage.append(name)
                        .append(": ")
                        .append(value)
                        .append("\n"));
            });

            if (clientRequest.body() != null) {
                return clientRequest.bodyToMono(String.class)
                        .doOnTerminate(() -> logRequestBody(logMessage, clientRequest))
                        .map(body -> clientRequest);
            } else {
                logRequestBody(logMessage, clientRequest);
                return Mono.just(clientRequest);
            }
        });
    }

    private void logRequestBody(StringBuilder logMessage, ClientRequest clientRequest) {
        if (clientRequest.body() != null) {
            clientRequest.bodyToMono(String.class)
                    .doOnTerminate(() -> {
                        logMessage.append("Body: ").append(clientRequest.body()).append("\n");
                        log.info(logMessage.toString());
                    })
                    .subscribe();
        } else {
            log.info(logMessage.toString());
        }
    }

    private ExchangeFilterFunction tokenExchangeFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // Attach token if it's available
            String token = getAccessToken();
            if (token != null) {
                return Mono.just(clientRequest.mutate()
                        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                        .build());
            }
            return Mono.just(clientRequest);
        });
    }

    // Get Access Token (either from cache or refresh if expired)
    private String getAccessToken() {
        if (isAccessTokenExpired()) {
            refreshAccessToken();
        }
        return accessToken;
    }

    private boolean isAccessTokenExpired() {
        long currentTime = System.currentTimeMillis();
        return currentTime >= accessTokenExpiry;
    }

    private void refreshAccessToken() {
        log.info("Refreshing Access Token...");
        WebClient client = WebClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();

        client.post()
                .uri("/v2/token/refresh")
                .bodyValue(Collections.singletonMap("refresh", refreshToken))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .doOnTerminate(() -> log.info("Access Token Refreshed"))
                .subscribe(response -> {
                    accessToken = response.getAccess();
                    accessTokenExpiry = System.currentTimeMillis() + (response.getAccessExpires() * 1000);
                    log.info("New Access Token: {}", accessToken);
                });
    }
}
@Getter @Setter
public static class TokenResponse {
        private String access;
        private Long accessExpires;
}