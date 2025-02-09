package com.iraychev.expenseanalyzer.config;

import com.iraychev.expenseanalyzer.interceptor.RateLimitingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RateLimitingInterceptor rateLimitingInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(rateLimitingInterceptor));
        return restTemplate;
    }
}