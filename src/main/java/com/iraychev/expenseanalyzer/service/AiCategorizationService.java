package com.iraychev.expenseanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class AiCategorizationService {

    static final String OTHER = "Other";
    private static final int BATCH_SIZE = 25;
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    public static final List<String> VALID_CATEGORIES = List.of(
            "Supermarkets",
            "Financial Services",
            "Shopping",
            "Restaurants and bars",
            "Entertainment and Sport",
            "Traveling and Vacation",
            "Health and Beauty"
    );

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.url}")
    private String baseApiUrl;

    private String apiUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiCategorizationService() {
        // Todo: Use web/rest client instead
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        this.apiUrl = baseApiUrl + "?key=" + apiKey;
    }

    public String categorizeRemittance(String remittanceInfoUnstructured) {
        if (remittanceInfoUnstructured == null || remittanceInfoUnstructured.trim().isEmpty()) {
            log.error("Empty remittance info");
            return OTHER;
        }
        return categorizeRemittances(List.of(remittanceInfoUnstructured)).get(0);
    }

    /**
     * Categorizes all descriptions with one request per {@value #BATCH_SIZE} descriptions.
     * The result has the same size and order as the input; anything the model could not answer is "Other".
     */
    public List<String> categorizeRemittances(List<String> descriptions) {
        List<String> result = new ArrayList<>(Collections.nCopies(descriptions.size(), OTHER));
        for (int from = 0; from < descriptions.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, descriptions.size());
            List<String> answers = categorizeBatch(descriptions.subList(from, to));
            for (int i = 0; i < answers.size(); i++) {
                result.set(from + i, answers.get(i));
            }
        }
        return result;
    }

    private List<String> categorizeBatch(List<String> descriptions) {
        List<String> failed = Collections.nCopies(descriptions.size(), OTHER);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(descriptions)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("API response status: {}", response.statusCode());
            if (response.statusCode() != 200) {
                log.error("API request failed with status: {}", response.statusCode());
                return failed;
            }
            return parseResponse(response.body(), descriptions.size());
        } catch (InterruptedException e) {
            log.error("Request interrupted", e);
            Thread.currentThread().interrupt();
            return failed;
        } catch (Exception e) {
            log.error("Error during categorization", e);
            return failed;
        }
    }

    private String buildRequestBody(List<String> descriptions) throws Exception {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < descriptions.size(); i++) {
            String description = descriptions.get(i);
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, MAX_DESCRIPTION_LENGTH);
            }
            list.append(i + 1).append(". ").append(description).append('\n');
        }
        String prompt = "Categorize each bank transaction below. Respond with a JSON array with exactly one category "
                + "per transaction, in the same order. Use \"" + OTHER + "\" when none of the categories fit.\n"
                + list;

        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);

        ObjectNode config = body.putObject("generationConfig");
        config.put("temperature", 0.0);
        config.put("maxOutputTokens", 1024);
        config.put("responseMimeType", "application/json");
        ObjectNode schema = config.putObject("responseSchema");
        schema.put("type", "ARRAY");
        ObjectNode items = schema.putObject("items");
        items.put("type", "STRING");
        ArrayNode allowed = items.putArray("enum");
        VALID_CATEGORIES.forEach(allowed::add);
        allowed.add(OTHER);

        return objectMapper.writeValueAsString(body);
    }

    private List<String> parseResponse(String responseBody, int expected) throws Exception {
        String text = objectMapper.readTree(responseBody)
                .path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        JsonNode answers = objectMapper.readTree(text);
        if (!answers.isArray() || answers.size() != expected) {
            log.error("Expected {} categories but got: {}", expected, text);
            return Collections.nCopies(expected, OTHER);
        }
        List<String> categories = new ArrayList<>();
        for (JsonNode answer : answers) {
            categories.add(normalize(answer.asText()));
        }
        return categories;
    }

    private String normalize(String category) {
        return VALID_CATEGORIES.stream()
                .filter(valid -> valid.equalsIgnoreCase(category.trim()))
                .findFirst()
                .orElse(OTHER);
    }
}
