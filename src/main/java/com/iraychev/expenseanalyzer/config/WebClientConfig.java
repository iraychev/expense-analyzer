package com.iraychev.expenseanalyzer.config;

import com.iraychev.expenseanalyzer.dto.TokenResponse;
import com.iraychev.expenseanalyzer.exception.BankIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${gocardless.api.base-url}")
    private String apiBaseUrl;

    @Value("${gocardless.api.refresh-token}")
    private String refreshToken;

    private volatile String accessToken;
    private volatile Long accessTokenExpiry;
    private final Object tokenLock = new Object();

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .filter(logRequest())
                .filter(logResponse())
                .filter(tokenExchangeFilter())
                .filter(throwOnErrorFilter())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return (clientRequest, next) -> {
            log.info("Request received: {} {}", clientRequest.method(), clientRequest.url());
            return next.exchange(clientRequest);
        };
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response ->
                DataBufferUtils.join(response.bodyToFlux(DataBuffer.class))
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            String body = new String(bytes, StandardCharsets.UTF_8);
                            log.info("Response Body: {}", body);

                            DataBuffer newBuffer = dataBuffer.factory().wrap(bytes);
                            return Mono.just(
                                    ClientResponse.from(response)
                                            .body(Flux.just(newBuffer))
                                            .build()
                            );
                        })
                        .defaultIfEmpty(ClientResponse.from(response).build())
        );
    }

    private ExchangeFilterFunction tokenExchangeFilter() {
        return (clientRequest, next) -> {
            if (clientRequest.url().getPath().contains("/token/")) {
                return next.exchange(clientRequest);
            }
            return Mono.just(getAccessToken())
                    .flatMap(token -> next.exchange(
                            ClientRequest.from(clientRequest)
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .build()
                    ));
        };
    }

    private String getAccessToken() {
        if (isAccessTokenExpired()) {
            synchronized (tokenLock) {
                if (isAccessTokenExpired()) {
                    refreshAccessToken();
                }
            }
        }
        return accessToken;
    }

    private boolean isAccessTokenExpired() {
        return accessToken == null || System.currentTimeMillis() >= accessTokenExpiry;
    }

    private void refreshAccessToken() {
        log.info("Refreshing access token...");
        TokenResponse response = WebClient.create(apiBaseUrl)
                .post()
                .uri("/token/refresh/")
                .bodyValue(Collections.singletonMap("refresh", refreshToken))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();

        this.accessToken = response.getAccess();
        this.accessTokenExpiry = System.currentTimeMillis() + (response.getAccessExpires() * 1000);
        log.info("New access token expires at: {}", accessTokenExpiry);
    }

    private ExchangeFilterFunction throwOnErrorFilter() {
        return (clientRequest, next) -> next.exchange(clientRequest)
                .flatMap(response -> {
                    if (response.statusCode().isError()) {
                        return DataBufferUtils.join(response.bodyToFlux(DataBuffer.class))
                                .flatMap(dataBuffer -> {
                                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(bytes);
                                    DataBufferUtils.release(dataBuffer);
                                    String responseBody = new String(bytes, StandardCharsets.UTF_8);

                                    return Mono.error(new BankIntegrationException(
                                            "GoCardless API Error: " + responseBody));
                                });
                    }
                    return Mono.just(response);
                });
    }
}