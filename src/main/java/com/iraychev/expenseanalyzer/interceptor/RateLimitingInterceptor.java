package com.iraychev.expenseanalyzer.interceptor;

import lombok.RequiredArgsConstructor;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimitingInterceptor implements ClientHttpRequestInterceptor {
    private final RateLimiter rateLimiter = RateLimiter.create(10);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        rateLimiter.acquire();

        try {
            ClientHttpResponse response = execution.execute(request, body);
            handleRateLimitHeaders(response);
            return response;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                handleRateLimitExceeded(e);
                return intercept(request, body, execution);
            }
            throw e;
        }
    }

    private void handleRateLimitHeaders(ClientHttpResponse response) {
        String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
        String reset = response.getHeaders().getFirst("X-RateLimit-Reset");

        if (remaining != null && Integer.parseInt(remaining) < 5) {
            rateLimiter.setRate(Double.parseDouble(remaining) / 2);
        }
    }

    private void handleRateLimitExceeded(HttpStatusCodeException e) {
        String retryAfter = e.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                Thread.sleep(Long.parseLong(retryAfter) * 1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}