package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class CustomResponseStatusException extends ResponseStatusException {
    private final String errorCode;
    private final String message;
    private final String field;

    public CustomResponseStatusException(HttpStatusCode status, String errorCode, String reason, String message) {
        this(status, errorCode, reason, message, null);
    }

    public CustomResponseStatusException(HttpStatusCode status, String errorCode, String reason, String message,
                                         String field) {
        super(status, reason);
        this.errorCode = errorCode;
        this.message = message;
        this.field = field;
    }
}
