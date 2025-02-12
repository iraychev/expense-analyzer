package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class CustomResponseStatusException extends ResponseStatusException {
    private final String errorCode;
    private final String message;

    public CustomResponseStatusException(HttpStatusCode status, String errorCode, String reason, String message) {
        super(status, reason);
        this.errorCode = errorCode;
        this.message = message;
    }
}
