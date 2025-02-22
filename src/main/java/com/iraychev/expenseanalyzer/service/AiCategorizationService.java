package com.iraychev.expenseanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class AiCategorizationService {

    @Value("${ai.api.key}")
    private String apiKey;
    private String apiUrl;
    private final List<String> validCategories = Arrays.asList(
            "Supermarkets",
            "Financial Services",
            "Shopping",
            "Restaurants and bars",
            "Entertainment and Sport",
            "Traveling and Vacation",
            "Health and Beauty"
    );
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiCategorizationService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey;
    }

    public String categorizeRemittance(String remittanceInfoUnstructured) {
        if (remittanceInfoUnstructured == null || remittanceInfoUnstructured.trim().isEmpty()) {
            log.error("Empty remittance info");
            return "Other";
        }

        try {
            String requestBody = buildRequestBody(remittanceInfoUnstructured);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("API response status: {}", response.statusCode());
            log.trace("API response body: {}", response.body());

            if (response.statusCode() != 200) {
                log.error("API request failed with status: {}", response.statusCode());
                return "Other";
            }

            return parseResponse(response.body());

        } catch (IOException e) {
            log.error("IO error during API request", e);
            return "Other";
        } catch (InterruptedException e) {
            log.error("Request interrupted", e);
            Thread.currentThread().interrupt();
            return "Other";
        } catch (Exception e) {
            log.error("Unexpected error during categorization", e);
            return "Other";
        }
    }

    private String buildRequestBody(String remittanceInfo) throws IOException {
        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode contents = requestBody.putArray("contents");

        ObjectNode contentNode = contents.addObject();
        ArrayNode parts = contentNode.putArray("parts");
        ObjectNode textPart = parts.addObject();

        String prompt = String.format(
                "Analyze the following transaction details and respond ONLY with the matching category name from the list. "
                        + "Transaction details: %s "
                        + "Possible categories: %s",
                remittanceInfo,
                String.join(", ", validCategories)
        );
        textPart.put("text", prompt);

        ObjectNode config = requestBody.putObject("generationConfig");
        config.put("maxOutputTokens", 50);
        config.put("temperature", 0.0);

        return objectMapper.writeValueAsString(requestBody);
    }

    private String parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty() || !candidates.isArray()) {
            log.error("No candidates in API response");
            return "Other";
        }

        JsonNode firstCandidate = candidates.get(0);
        if (firstCandidate == null) {
            log.error("Empty first candidate");
            return "Other";
        }

        JsonNode content = firstCandidate.path("content");
        if (content.isMissingNode()) {
            log.error("Missing content in candidate");
            return "Other";
        }

        JsonNode parts = content.path("parts");
        if (parts.isEmpty() || !parts.isArray()) {
            log.error("No parts in content");
            return "Other";
        }

        JsonNode firstPart = parts.get(0);
        if (firstPart == null) {
            log.error("Empty first part");
            return "Other";
        }

        String category = firstPart.path("text").asText().trim();
        log.debug("Raw AI response category: {}", category);

        return validateAndNormalizeCategory(category);
    }

    private String validateAndNormalizeCategory(String category) {
        String cleanedCategory = category.replaceAll("^[\"']+|[\"']+$", "")
                .replace("&", "and")
                .trim();

        return validCategories.stream()
                .filter(validCategory -> validCategory.equalsIgnoreCase(cleanedCategory))
                .findFirst()
                .orElse("Other");
    }
}