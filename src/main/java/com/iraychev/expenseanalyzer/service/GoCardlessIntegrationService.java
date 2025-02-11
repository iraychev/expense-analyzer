package com.iraychev.expenseanalyzer.service;

import com.gocardless.GoCardlessClient;
import com.gocardless.resources.Payment;
import com.iraychev.expenseanalyzer.domain.entity.Transaction;
import com.iraychev.expenseanalyzer.dto.RequisitionDto;
import com.iraychev.expenseanalyzer.dto.RequisitionRequestDto;
import com.iraychev.expenseanalyzer.dto.TransactionsResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoCardlessIntegrationService {

    private final WebClient webClient;

    @Value("${gocardless.api.base-url}")
    private String apiBaseUrl;

    // In production the access token should be securely managed.
    @Value("${gocardless.api.access-token}")
    private String accessToken;

    public GoCardlessIntegrationService(WebClient webClient) {
        this.webClient = webClient;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("redirect", requestDto.getRedirectUrl());
        payload.put("institution_id", requestDto.getInstitutionId());
        payload.put("reference", requestDto.getReference());

        payload.put("user_language", requestDto.getUserLanguage());
        log.info("Request DTO: {}", requestDto);
        log.info("Request body: {}", payload);
        return webClient.post()
                .uri(apiBaseUrl + "/requisitions/")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(RequisitionDto.class)
                .block();
    }

    // New helper: retrieve requisition details (for listing accounts)
    public RequisitionDto getRequisition(String requisitionId) {
        return webClient.get()
                .uri(apiBaseUrl + "/requisitions/" + requisitionId + "/")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(RequisitionDto.class)
                .block();
    }

    public List<Transaction> fetchTransactions(String externalAccountId, String accessTokenHeader) {
        TransactionsResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(apiBaseUrl + "/accounts/{accountId}/transactions/")
                        .queryParam("date_from", LocalDate.now().minusMonths(3))
                        .queryParam("date_to", LocalDate.now())
                        .build(externalAccountId))
                .header("Authorization", "Bearer " + accessTokenHeader)
                .retrieve()
                .bodyToMono(TransactionsResponse.class)
                .block();
        return response != null ? response.getTransactions() : Collections.emptyList();
    }
}
