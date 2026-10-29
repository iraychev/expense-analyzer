package com.iraychev.expenseanalyzer.categorization.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iraychev.expenseanalyzer.categorization.TransactionDirection;
import com.iraychev.expenseanalyzer.service.AiCategorizationService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Talks to a local HTTP server that plays Gemini. */
class GeminiRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AiCategorizationService serviceAnswering(int status, String body,
                                                     AtomicReference<String> path, AtomicReference<String> request,
                                                     AtomicReference<String> keyHeader) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().toString());
            keyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        AiCategorizationService service = new AiCategorizationService();
        ReflectionTestUtils.setField(service, "apiKey", "secret-key");
        ReflectionTestUtils.setField(service, "baseApiUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/models");
        ReflectionTestUtils.setField(service, "model", "gemini-test-1");
        return service;
    }

    private static String geminiAnswer(String json) throws Exception {
        return MAPPER.writeValueAsString(Map.of("candidates", List.of(Map.of("content",
                Map.of("parts", List.of(Map.of("text", json)))))));
    }

    @Test
    void sendsOneStructuredRequestForTheWholeBatchWithPinnedModel() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> request = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AiCategorizationService service = serviceAnswering(200, geminiAnswer(
                "[{\"id\":\"0\",\"category\":\"Supermarkets\"},{\"id\":\"1\",\"category\":\"UNKNOWN\"},"
                        + "{\"id\":\"2\",\"category\":\"Not A Category\"},{\"id\":\"99\",\"category\":\"Shopping\"}]"),
                path, request, key);

        Map<String, String> answers = service.categorize(List.of(
                new AiItem("0", "Card payment at: billa", TransactionDirection.EXPENSE),
                new AiItem("1", "Card payment at: xyz", TransactionDirection.EXPENSE),
                new AiItem("2", "Card payment at: abc", TransactionDirection.EXPENSE)));

        assertEquals("/v1beta/models/gemini-test-1:generateContent", path.get());
        assertEquals("secret-key", key.get());
        assertEquals("Supermarkets", answers.get("0"));
        assertEquals("UNKNOWN", answers.get("1"));
        assertEquals("UNKNOWN", answers.get("2"), "values outside the schema are treated as unknown");
        assertEquals(3, answers.size(), "ids that were not asked for are ignored");

        JsonNode body = MAPPER.readTree(request.get());
        assertEquals("application/json", body.path("generationConfig").path("responseMimeType").asText());
        JsonNode enumNode = body.path("generationConfig").path("responseSchema").path("items")
                .path("properties").path("category").path("enum");
        List<String> allowed = new ArrayList<>();
        enumNode.forEach(n -> allowed.add(n.asText()));
        assertTrue(allowed.containsAll(List.of("Supermarkets", "Fees", "Salary", "Other income", "Other", "UNKNOWN")));
        assertTrue(!allowed.contains("Transfer in") && !allowed.contains("Transfer out"));
        String prompt = body.path("contents").path(0).path("parts").path(0).path("text").asText();
        assertTrue(prompt.contains("0 | EXPENSE | Card payment at: billa"));
        assertTrue(prompt.contains("2 | EXPENSE | Card payment at: abc"));
    }

    @Test
    void rateLimitIsReportedAsUnavailableWithStatus() throws Exception {
        AiCategorizationService service = serviceAnswering(429, "{}", new AtomicReference<>(),
                new AtomicReference<>(), new AtomicReference<>());

        AiUnavailableException e = assertThrows(AiUnavailableException.class, () -> service.categorize(
                List.of(new AiItem("0", "x", TransactionDirection.EXPENSE))));

        assertTrue(e.isRateLimited());
    }

    @Test
    void garbageResponseIsUnavailableNotACategory() throws Exception {
        AiCategorizationService service = serviceAnswering(200, geminiAnswer("not json"), new AtomicReference<>(),
                new AtomicReference<>(), new AtomicReference<>());

        assertThrows(AiUnavailableException.class, () -> service.categorize(
                List.of(new AiItem("0", "x", TransactionDirection.EXPENSE))));
    }
}
