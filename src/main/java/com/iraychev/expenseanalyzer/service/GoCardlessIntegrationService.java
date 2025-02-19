package com.iraychev.expenseanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iraychev.expenseanalyzer.domain.enums.TransactionType;
import com.iraychev.expenseanalyzer.dto.*;
import com.iraychev.expenseanalyzer.exception.BankIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoCardlessIntegrationService {
    private final WebClient webClient;

    @Value("${gocardless.api.base-url}")
    private String apiBaseUrl;

    public RequisitionDto createRequisition(RequisitionRequestDto requestDto) {
        Map<String, Object> payload = Map.of(
                "redirect", requestDto.getRedirect(),
                "institution_id", requestDto.getInstitutionId(),
                "reference", requestDto.getReference(),
                "user_language", requestDto.getUserLanguage()
        );

        log.info("Request DTO: {}", requestDto);
        log.info("Request body: {}", payload);

        return webClient.post()
                .uri(apiBaseUrl + "/requisitions/")
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toEntity(RequisitionDto.class)
                .doOnNext(entity -> log.info("Response status: {}, body: {}", entity.getStatusCode(), entity.getBody()))
                .flatMap(responseEntity -> {
                    if (responseEntity.getBody() == null) {
                        log.error("Received 201 Created with empty body. Check API expectations.");
                        return Mono.error(new BankIntegrationException("Empty body response"));
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

    private String decodeUnicode(String input) {
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String unicodeChar = String.valueOf((char) Integer.parseInt(matcher.group(1), 16));
            matcher.appendReplacement(result, unicodeChar);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private List<TransactionDto> parseTransactions(List<BankAccountDto> accounts, JsonNode transactionsNode) {
        List<TransactionDto> transactions = new ArrayList<>();
        
        if (transactionsNode.isArray()) {
            for (JsonNode transactionNode : transactionsNode) {
                TransactionDto transactionDto = TransactionDto.builder()
                        .amount(new BigDecimal(transactionNode.path("transactionAmount").path("amount").asText()))
                        .currency(transactionNode.path("transactionAmount").path("currency").asText())
                        .valueDate(LocalDate.parse(transactionNode.path("valueDate").asText()).atStartOfDay())
                        .transactionDate(LocalDate.parse(transactionNode.path("bookingDate").asText()).atStartOfDay())
                        .description(decodeUnicode(transactionNode.path("remittanceInformationUnstructured").asText())) // Decoding here
                        .type(TransactionType.UNKNOWN)
                        .bankAccountId(accounts.get(0).getId())
                        .build();
                transactions.add(transactionDto);
            }
        }
        return transactions;
    }

    public List<BankAccountDto> fetchTransactions(List<BankAccountDto> accounts) {
        if (true) { // For testing purposes, using cached transactions
            return getCachedTransactions(accounts);
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
                        List<TransactionDto> parsedTransactions = parseTransactions(accounts, transactionsNode);
                        allTransactions.addAll(parsedTransactions);
                    });
            account.setTransactions(allTransactions.stream()
                    .filter(transactionDto -> transactionDto.getBankAccountId().equals(account.getId()))
                    .collect(Collectors.toList())
            );
        });

        return accounts;
    }

    public List<BankAccountDto> getCachedTransactions(List<BankAccountDto> accounts) {
        Resource resource = new ClassPathResource("cached_transactions_unicredit.json");

        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                JsonNode rootNode = new ObjectMapper().readTree(in);
                JsonNode bookedTransactions = rootNode.path("transactions").path("booked");

                List<TransactionDto> transactions = parseTransactions(accounts, bookedTransactions);
                accounts.get(0).setTransactions(transactions);

                return accounts;
            } catch (IOException e) {
                log.error("Failed to read cached transactions", e);
            }
        }
        log.error("Couldn't read anything cached");
        return accounts;
    }
}