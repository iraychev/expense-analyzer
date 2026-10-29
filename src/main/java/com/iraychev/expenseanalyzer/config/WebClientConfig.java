package com.iraychev.expenseanalyzer.config;

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
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${gocardless.api.base-url}")
    private String apiBaseUrl;

    private final GoCardlessTokenProvider tokenProvider;

    public WebClientConfig(GoCardlessTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

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
            // Token renewal may block on an HTTP call, so keep it off the event loop.
            return Mono.fromCallable(tokenProvider::getAccessToken)
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(token -> next.exchange(
                            ClientRequest.from(clientRequest)
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .build()
                    ));
        };
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