package com.iraychev.expenseanalyzer.config;

import com.iraychev.expenseanalyzer.exception.RateLimitException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebClientRateLimitTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void goCardless429BecomesRateLimitException() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"summary\":\"Rate limit exceeded\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        GoCardlessTokenProvider tokenProvider = mock(GoCardlessTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token");
        WebClientConfig config = new WebClientConfig(tokenProvider);
        ReflectionTestUtils.setField(config, "apiBaseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        WebClient client = config.webClient();

        RateLimitException e = assertThrows(RateLimitException.class,
                () -> client.get().uri("/accounts/x/transactions/").retrieve().bodyToMono(String.class).block());

        assertEquals(429, e.getStatusCode().value());
        assertEquals("ERR007", e.getErrorCode());
    }
}
