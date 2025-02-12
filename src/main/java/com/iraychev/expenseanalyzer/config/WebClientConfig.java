package com.iraychev.expenseanalyzer.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Slf4j
@Configuration
@EnableCaching
public class WebClientConfig {

    @Value("${gocardless.api.base-url}")
    private String apiBaseUrl;

    @Value("${gocardless.api.refresh-token}")
    private String refreshToken;

    @Value("${gocardless.api.access-token-expiry}")
    private Long accessTokenExpiry;

    private static final String BEARER = "Bearer ";
    private String accessToken;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .baseUrl(apiBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .filter(logRequest())
                .filter(tokenExchangeFilter())
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

            return clientRequest.bodyToMono(String.class)
                    .doOnNext(body -> logMessage.append("Body: ").append(body).append("\n"))
                    .doOnTerminate(() -> log.info(logMessage.toString()))
                    .then(Mono.just(clientRequest));
        });
    }

    private ExchangeFilterFunction tokenExchangeFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            String token = getAccessToken();
            if (token != null) {
                return Mono.just(clientRequest.mutate()
                        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                        .build());
            }
            return Mono.just(clientRequest);
        });
    }

    @Cacheable("accessToken")
    public String getAccessToken() {
        if (isAccessTokenExpired()) {
            refreshAccessToken();
        }
        return accessToken;
    }

    private boolean isAccessTokenExpired() {
        long currentTime = System.currentTimeMillis();
        return currentTime >= accessTokenExpiry;
    }

    @CacheEvict(value = "accessToken", allEntries = true)
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
                .doOnNext(response -> {
                    accessToken = response.getAccess();
                    accessTokenExpiry = System.currentTimeMillis() + (response.getAccessExpires() * 1000);
                    log.info("New Access Token: {}", accessToken);
                })
                .block();
    }
}

@Getter @Setter
public static class TokenResponse {
    private String access;
    private Long accessExpires;
}