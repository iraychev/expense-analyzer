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
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;

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

    private String getBankAccountIban(String accountId) {
        String responseBody = webClient.get()
                .uri(apiBaseUrl + "/accounts/" + accountId + "/")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("iban").asText(null);
        } catch (Exception e) {
            throw new BankIntegrationException("Error parsing JSON response");
        }
    }

    public List<BankAccountDto> updateBankAccountsWithFetchedTransactions(List<BankAccountDto> accounts) {
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
                        List<TransactionDto> parsedTransactions = parseTransactions(account, transactionsNode);
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

        for (BankAccountDto account : accounts) {
            String accountIban = account.getIban();
            if (account.getIban() == null || account.getIban().equals("Not yet known")) {
                log.info("Account iban is {}", account.getIban());
                accountIban = getBankAccountIban(account.getAccountId());
                account.setIban(accountIban);
            }

            Resource resource = getResourceFromIban(accountIban);

            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    JsonNode rootNode = new ObjectMapper().readTree(in);
                    JsonNode bookedTransactions = rootNode.path("transactions").path("booked");

                    List<TransactionDto> transactions = parseTransactions(account, bookedTransactions);
                    account.setTransactions(transactions);

                } catch (IOException e) {
                    log.error("Failed to read cached transactions for accounts {}", account, e);
                }
            }
        }
        return accounts;
    }

    private Resource getResourceFromIban(String iban) {
        Resource resource;

        switch (iban) {
            case "LT143250059635469546" -> resource = new ClassPathResource("cached_transactions_revolut.json");
            case "BG16UNCR70001598168484" -> resource = new ClassPathResource("cached_transactions_unicredit.json");
            case "BG68STSA93000027489927" -> resource = new ClassPathResource("cached_transactions_dsk.json");
            default -> throw new BankIntegrationException("No cached transactions for account with IBAN: " + iban);
        }
        return resource;
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
    private List<TransactionDto> parseTransactions(BankAccountDto account, JsonNode transactionsNode) {
        List<TransactionDto> transactions = new ArrayList<>();

        if (transactionsNode.isArray()) {
            for (JsonNode transactionNode : transactionsNode) {
                String proprietaryBankTransactionCode = transactionNode.path("proprietaryBankTransactionCode").asText();

                TransactionType type = switch (proprietaryBankTransactionCode) {
                    case "TRANSFER" -> TransactionType.TRANSFER;
                    case "CARD_PAYMENT", "AC1" -> TransactionType.CARD_PAYMENT;
                    case "CREDIT" -> TransactionType.INCOME;
                    default -> TransactionType.UNKNOWN;
                };

                String remittanceInformationUnstructured = transactionNode.path("remittanceInformationUnstructured").asText();
                if (remittanceInformationUnstructured.isEmpty()) {
                    JsonNode remittanceInformationUnstructuredArray = transactionNode.path("remittanceInformationUnstructuredArray");
                    if (remittanceInformationUnstructuredArray.isArray()) {
                        StringBuilder combinedRemittanceInfo = new StringBuilder();
                        for (JsonNode element : remittanceInformationUnstructuredArray) {
                            combinedRemittanceInfo.append(element.asText()).append(" ");
                        }
                        remittanceInformationUnstructured = combinedRemittanceInfo.toString().trim();
                    }
                }

                String decodedRemittanceInfo = decodeUnicode(remittanceInformationUnstructured);
                TransactionDto transactionDto = TransactionDto.builder()
                        .amount(new BigDecimal(transactionNode.path("transactionAmount").path("amount").asText()))
                        .currency(transactionNode.path("transactionAmount").path("currency").asText())
                        .valueDate(LocalDate.parse(transactionNode.path("valueDate").asText()).atStartOfDay())
                        .transactionDate(LocalDate.parse(transactionNode.path("bookingDate").asText()).atStartOfDay())
                        .description(decodedRemittanceInfo)
                        .type(type)
                        .category(categoryService.categorizeTransaction(decodedRemittanceInfo))
                        .bankAccountId(account.getId())
                        .build();
                transactions.add(transactionDto);
            }
        }
        return transactions;
    }
}