package com.iraychev.expenseanalyzer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
public class WebClientConfig {
    @Value("${gocardless.api.base-url}")
    private String apiBaseUrl;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .baseUrl(apiBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .filter(logRequest())
                .build();
    }

     private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // Capture and log the request method, URL, headers, and body
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("Request: ")
                    .append(clientRequest.method())
                    .append(" ")
                    .append(clientRequest.url())
                    .append("\n");

            // Log headers
            clientRequest.headers().forEach((name, values) -> {
                values.forEach(value -> logMessage.append(name)
                        .append(": ")
                        .append(value)
                        .append("\n"));
            });

            // Capture and log request body (if present)
            if (clientRequest.body() != null) {
                return clientRequest.bodyToMono(String.class)
                        .doOnTerminate(() -> logRequestBody(logMessage.toString(), clientRequest))
                        .map(body -> clientRequest);
            } else {
                logRequestBody(logMessage.toString(), clientRequest);
                return Mono.just(clientRequest);
            }
        });
    }

    private void logRequestBody(StringBuilder logMessage, ClientRequest clientRequest) {
        // If the request body is available, log it
        clientRequest.bodyToMono(String.class)
                .doOnTerminate(() -> {
                    logMessage.append("Body: ").append(clientRequest.body()).append("\n");
                    log.info(logMessage.toString()); // Log the complete message at INFO level
                });
    }
}