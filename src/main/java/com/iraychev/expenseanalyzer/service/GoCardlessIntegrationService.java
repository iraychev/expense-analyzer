package com.iraychev.expenseanalyzer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import com.iraychev.expenseanalyzer.dto.*;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

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

    public List<BankAccountDto> fetchTransactions(List<BankAccountDto> accounts) {
        ObjectMapper objectMapper = new ObjectMapper();
        Resource resource = new ClassPathResource("cached_transactions.json");

        // Try to load cached transactions from the classpath
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                JsonNode rootNode = objectMapper.readTree(in);
                JsonNode bookedTransactions = rootNode
                        .path("transactions")
                        .path("booked");

                List<TransactionDto> transactions = new ArrayList<>();
                for (JsonNode txNode : bookedTransactions) {
                    TransactionDto dto = TransactionDto.builder()
                            .amount(new BigDecimal(txNode.path("transactionAmount").path("amount").asText()))
                            .currency(txNode.path("transactionAmount").path("currency").asText())
                            .valueDate(LocalDate.parse(txNode.path("valueDate").asText()))
                            .transactionDate(LocalDate.parse(txNode.path("bookingDate").asText()))
                            .description(txNode.path("remittanceInformationUnstructured").asText())
                            .bankAccount(accounts.getFirst())
                            .build();
                    transactions.add(dto);
                }
                accounts.getFirst().setTransactions(transactions);
                log.info("transactions:");
                log.info(transactions.toString());
                log.info("accounts:");
                log.info(accounts.toString());

                return accounts;
            } catch (IOException e) {
                log.error("Failed to read cached transactions", e);
            }
        }

        List<TransactionDto> allTransactions = new ArrayList<>();
        accounts.forEach(account -> {
            webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/accounts/" + account.getAccountId() + "/transactions/")
                            .queryParam("date_from", LocalDate.now().minusMonths(3))
                            .queryParam("date_to", LocalDate.now(ZoneOffset.UTC))
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
                                        .bankAccount(account)
                                        .build();
                                allTransactions.add(transactionDto);
                            }
                        }
                    });
            account.setTransactions(allTransactions.stream()
                    .filter(transactionDto -> transactionDto.getBankAccount().equals(account)) // Filter transactions for this account
                    .collect(Collectors.toList()) // Collect them into a list
            );
        });

        return accounts;
    }
}
