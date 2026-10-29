package com.iraychev.expenseanalyzer.categorization.ai;

import lombok.Getter;

/** The AI could not produce answers (as opposed to answering "unknown"). */
@Getter
public class AiUnavailableException extends Exception {

    /** Status used when the circuit breaker rejected the call without contacting the AI. */
    public static final int CIRCUIT_OPEN = -1;

    /** HTTP status of the failed call, 0 for network/parse problems, {@link #CIRCUIT_OPEN} for a rejected call. */
    private final int statusCode;

    public AiUnavailableException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public AiUnavailableException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public static AiUnavailableException circuitOpen() {
        return new AiUnavailableException("AI circuit breaker is open", CIRCUIT_OPEN);
    }

    public boolean isCircuitOpen() {
        return statusCode == CIRCUIT_OPEN;
    }

    public boolean isRateLimited() {
        return statusCode == 429;
    }

    public boolean isServerError() {
        return statusCode >= 500;
    }

    /** Worth another attempt: rate limit, server error, network or response problems. Not auth/model/request errors. */
    public boolean isTransient() {
        return isRateLimited() || isServerError() || statusCode == 0;
    }
}
