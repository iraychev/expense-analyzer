package com.iraychev.expenseanalyzer.service;

import com.gocardless.GoCardlessClient;
import com.gocardless.resources.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoCardlessService {
    private final GoCardlessClient client;

    public GoCardlessService(@Value("${gocardless.access-token}") String accessToken,
                             @Value("${gocardless.environment}") String environment) {
        GoCardlessClient.Environment gcEnv = GoCardlessClient.Environment.valueOf(environment.toUpperCase());
        this.client = GoCardlessClient.newBuilder(accessToken)
                .withEnvironment(gcEnv)
                .build();
    }

    public List<Payment> fetchPayments(String accountId) {
        return client.payments()
                .list()
                .withCustomer(accountId)
                .execute()
                .getItems();
    }
}