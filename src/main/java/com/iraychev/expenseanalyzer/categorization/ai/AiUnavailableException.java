package com.iraychev.expenseanalyzer.categorization.ai;

import lombok.Getter;

/** The AI could not produce answers (as opposed to answering "unknown"). */
@Getter
public class AiUnavailableException extends Exception {

    /** HTTP status of the failed call, or 0 for network/parse problems. */
    private final int statusCode;

    public AiUnavailableException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public AiUnavailableException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    /** Rate limited or server side error: worth retrying later. */
    public boolean isRateLimited() {
        return statusCode == 429;
    }

    public boolean isServerError() {
        return statusCode >= 500;
    }
}
