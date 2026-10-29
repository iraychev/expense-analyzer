package com.iraychev.expenseanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iraychev.expenseanalyzer.categorization.Category;
import com.iraychev.expenseanalyzer.categorization.TransactionDirection;
import com.iraychev.expenseanalyzer.categorization.ai.AiCategorizer;
import com.iraychev.expenseanalyzer.categorization.ai.AiItem;
import com.iraychev.expenseanalyzer.categorization.ai.AiUnavailableException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gemini backed {@link AiCategorizer}. One request categorizes a whole batch of distinct merchants and uses
 * structured output: the response schema constrains every answer to one of the known category keys (or UNKNOWN).
 * The model id is pinned through {@code ai.api.model} instead of a floating "latest" alias.
 */
@Service
@Slf4j
public class AiCategorizationService implements AiCategorizer {

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.base-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseApiUrl;

    @Value("${ai.api.model:gemini-2.5-flash}")
    private String model;

    @Value("${ai.api.timeout-seconds:60}")
    private long timeoutSeconds = 60;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiCategorizationService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    void validateConfig() {
        if (model == null || model.isBlank() || model.toLowerCase().contains("latest")) {
            log.warn("ai.api.model is '{}': pin an explicit model id so categorization does not change silently", model);
        }
    }

    /** Single-item convenience used by the debug endpoint; never throws. */
    public String categorizeRemittance(String remittanceInfoUnstructured) {
        if (remittanceInfoUnstructured == null || remittanceInfoUnstructured.trim().isEmpty()) {
            return Category.OTHER.key();
        }
        try {
            Map<String, String> answers = categorize(List.of(
                    new AiItem("0", remittanceInfoUnstructured, TransactionDirection.EXPENSE)));
            String answer = answers.get("0");
            return answer == null || AiCategorizer.UNKNOWN.equals(answer) ? Category.OTHER.key() : answer;
        } catch (AiUnavailableException e) {
            log.error("AI categorization failed: {}", e.getMessage());
            return Category.OTHER.key();
        }
    }

    @Override
    public Map<String, String> categorize(List<AiItem> items) throws AiUnavailableException {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        try {
            String body = objectMapper.writeValueAsString(buildRequest(items));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseApiUrl + "/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    // Header instead of a query parameter keeps the key out of logs and URLs
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("AI response status: {}", response.statusCode());
            if (response.statusCode() != 200) {
                throw new AiUnavailableException("AI request failed with status " + response.statusCode(),
                        response.statusCode());
            }
            return parseResponse(response.body(), items);
        } catch (IOException e) {
            throw new AiUnavailableException("AI request failed: " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI request interrupted", 0, e);
        }
    }

    ObjectNode buildRequest(List<AiItem> items) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode system = root.putObject("systemInstruction");
        system.putArray("parts").addObject().put("text",
                "You categorize bank transactions of a personal finance app. For every item answer with exactly one "
                        + "category. Use " + AiCategorizer.UNKNOWN + " only if the text gives no usable hint. "
                        + "Items marked EXPENSE are money spent, INCOME is money received. Categories: "
                        + Category.all().stream()
                        .filter(c -> c.kind() != Category.Kind.TRANSFER)
                        .map(Category::key).collect(Collectors.joining(", ")) + ".");

        StringBuilder prompt = new StringBuilder("Categorize these items:\n");
        for (AiItem item : items) {
            prompt.append(item.id()).append(" | ").append(item.direction()).append(" | ")
                    .append(item.text() == null ? "" : item.text().replace('\n', ' ')).append('\n');
        }
        root.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt.toString());

        ObjectNode config = root.putObject("generationConfig");
        config.put("temperature", 0.0);
        config.put("maxOutputTokens", 256 + items.size() * 40);
        config.put("responseMimeType", "application/json");
        // The answer is short and structured: do not spend the output budget on hidden reasoning.
        config.putObject("thinkingConfig").put("thinkingBudget", 0);

        ObjectNode schema = config.putObject("responseSchema");
        schema.put("type", "ARRAY");
        ObjectNode itemSchema = schema.putObject("items");
        itemSchema.put("type", "OBJECT");
        ObjectNode properties = itemSchema.putObject("properties");
        properties.putObject("id").put("type", "STRING");
        ObjectNode category = properties.putObject("category");
        category.put("type", "STRING");
        ArrayNode allowed = category.putArray("enum");
        Stream.concat(Category.all().stream().filter(c -> c.kind() != Category.Kind.TRANSFER).map(Category::key),
                Stream.of(AiCategorizer.UNKNOWN)).forEach(allowed::add);
        itemSchema.putArray("required").add("id").add("category");
        itemSchema.putArray("propertyOrdering").add("id").add("category");
        return root;
    }

    Map<String, String> parseResponse(String responseBody, List<AiItem> items) throws AiUnavailableException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new AiUnavailableException("AI response has no content", 0);
            }
            JsonNode answers = objectMapper.readTree(textNode.asText());
            if (!answers.isArray()) {
                throw new AiUnavailableException("AI response is not a JSON array", 0);
            }
            Map<String, String> requested = items.stream()
                    .collect(Collectors.toMap(AiItem::id, AiItem::text, (a, b) -> a));
            Map<String, String> result = new HashMap<>();
            for (JsonNode answer : answers) {
                String id = answer.path("id").asText(null);
                String category = answer.path("category").asText(null);
                if (id == null || !requested.containsKey(id)) {
                    continue;
                }
                result.put(id, normalize(category));
            }
            return result;
        } catch (IOException e) {
            throw new AiUnavailableException("AI response could not be parsed: " + e.getMessage(), 0, e);
        }
    }

    private static String normalize(String category) {
        return Category.fromKey(category)
                .filter(c -> c.kind() != Category.Kind.TRANSFER)
                .map(Category::key)
                .orElse(AiCategorizer.UNKNOWN);
    }
}
