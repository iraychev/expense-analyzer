package com.iraychev.expenseanalyzer.config;

import com.gocardless.GoCardlessClient;
import com.iraychev.expenseanalyzer.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

@Configuration
@RequiredArgsConstructor
public class GoCardlessConfig {
    private final TokenService tokenService;

    @Value("${gocardless.environment}")
    private String environment;

    @Bean
    public GoCardlessClient goCardlessClient() {
        GoCardlessClient.Environment gcEnv = GoCardlessClient.Environment.valueOf(environment.toUpperCase());
        return GoCardlessClient.newBuilder(String.valueOf((Supplier<String>) tokenService::getValidAccessToken))
                .withEnvironment(gcEnv)
                .build();
    }
}