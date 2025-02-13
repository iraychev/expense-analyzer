package com.iraychev.expenseanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import com.iraychev.expenseanalyzer.dto.*;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoCardlessIntegrationService {
    private final WebClient webClient;

    @Value("${gocardless.api.base-url}")
    private String apiBaseUrl;

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("redirect", requestDto.getRedirect());
        payload.put("institution_id", requestDto.getInstitutionId());
        payload.put("reference", requestDto.getReference());
        payload.put("user_language", requestDto.getUserLanguage());

        log.info("Request DTO: {}", requestDto);
        log.info("Request body: {}", payload);

        return webClient.post()
                .uri(apiBaseUrl + "/requisitions/")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toEntity(RequisitionDto.class)  // Capture the entire response entity
                .doOnNext(entity -> log.info("Response status: {}, body: {}", entity.getStatusCode(), entity.getBody()))
                .flatMap(responseEntity -> {
                    if (responseEntity.getBody() == null) {
                        log.error("Received 201 Created with empty body. Check API expectations.");
                        return Mono.error(new RuntimeException("Empty body response"));
                    }
                    return Mono.just(responseEntity.getBody());
                })
                .doOnNext(dto -> log.info("Response from GoCardless: {}", dto))
                .doOnError(error -> log.error("Error from GoCardless: ", error))
                .block();
    }

    public RequisitionDto getRequisition(String requisitionId) {
        return webClient.get()
                .uri(apiBaseUrl + "/requisitions/" + requisitionId + "/")
                .retrieve()
                .bodyToMono(RequisitionDto.class)
                .block();
    }

    public List<TransactionDto> fetchTransactions(List<BankAccountDto> accounts) {
        List<TransactionDto> allTransactions = new ArrayList<>();
        accounts.forEach(account -> {
        webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/accounts/" + account.getAccountId() + "/transactions/")
                .queryParam("date_from", LocalDate.now().minusMonths(3))
                .queryParam("date_to", LocalDate.now())
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .blockOptional()
            .ifPresent(response -> {
                JsonNode transactionsNode = response.path("transactions").path("booked");
                if (transactionsNode.isArray()) {
                    for (JsonNode transactionNode : transactionsNode) {
                        TransactionDto transactionDto = TransactionDto.builder()
                            .amount(new BigDecimal(transactionNode.path("transactionAmount").path("amount").asText()))
                            .currency(transactionNode.path("transactionAmount").path("currency").asText())
                            .valueDate(LocalDate.parse(transactionNode.path("valueDate").asText()))
                            .transactionDate(LocalDate.parse(transactionNode.path("bookingDate").asText()))
                            .description(transactionNode.path("remittanceInformationUnstructured").asText())
                            .type(TransactionType.UNKNOWN)
                            .bankConnection(new BankConnectionDto())
                            .build();
                        allTransactions.add(transactionDto);
                    }
                }
            });
        });
        
        return allTransactions;
    }
}
