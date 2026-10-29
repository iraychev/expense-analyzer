package com.iraychev.expenseanalyzer.exception;

import org.springframework.http.HttpStatus;

/** The bank data provider (or another upstream) rejected the call because a rate limit was hit. */
public class RateLimitException extends CustomResponseStatusException {
    public static final String DEFAULT_MESSAGE =
            "The bank limits how often transactions can be refreshed. Please try again later.";

    public RateLimitException() {
        this(DEFAULT_MESSAGE);
    }

    public RateLimitException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, ExceptionCode.RATE_LIMITED.getValue(),
                ExceptionCode.RATE_LIMITED.getReason(), message);
    }
}
