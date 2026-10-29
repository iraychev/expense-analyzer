package com.iraychev.expenseanalyzer.config;

import com.iraychev.expenseanalyzer.exception.BankIntegrationException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoCardlessTokenProviderTest {

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();

    private int accessExpires = 86400;
    private int refreshExpires = 2592000;
    private int refreshStatus = 200;
    private int newStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/token/new/", exchange -> {
            requests.add("new");
            respond(exchange, newStatus, String.format(
                    "{\"access\":\"access-%d\",\"access_expires\":%d,\"refresh\":\"refresh-%d\",\"refresh_expires\":%d}",
                    counter.incrementAndGet(), accessExpires, counter.get(), refreshExpires));
        });
        server.createContext("/token/refresh/", exchange -> {
            requests.add("refresh");
            respond(exchange, refreshStatus, String.format(
                    "{\"access\":\"access-%d\",\"access_expires\":86400}", counter.incrementAndGet()));
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private GoCardlessTokenProvider provider() {
        return new GoCardlessTokenProvider("http://localhost:" + server.getAddress().getPort(), "id", "key");
    }

    @Test
    void requestsNewPairOnFirstUseAndReusesValidToken() {
        GoCardlessTokenProvider provider = provider();

        assertEquals("access-1", provider.getAccessToken());
        assertEquals("access-1", provider.getAccessToken());

        assertEquals(List.of("new"), requests);
    }

    @Test
    void refreshesAccessTokenWhenItIsAboutToExpire() {
        accessExpires = 30; // inside the expiry margin
        GoCardlessTokenProvider provider = provider();

        provider.getAccessToken();
        provider.getAccessToken();

        assertEquals(List.of("new", "refresh"), requests);
    }

    @Test
    void requestsNewPairWhenRefreshTokenHasExpired() {
        accessExpires = 30;
        refreshExpires = 30;
        GoCardlessTokenProvider provider = provider();

        provider.getAccessToken();
        provider.getAccessToken();

        assertEquals(List.of("new", "new"), requests);
    }

    @Test
    void fallsBackToNewPairWhenRefreshIsRejected() {
        accessExpires = 30;
        refreshStatus = 401;
        GoCardlessTokenProvider provider = provider();

        provider.getAccessToken();
        provider.getAccessToken();

        assertEquals(List.of("new", "refresh", "new"), requests);
    }

    @Test
    void failsClearlyWhenSecretsAreRejected() {
        newStatus = 401;

        assertThrows(BankIntegrationException.class, () -> provider().getAccessToken());
    }
}
