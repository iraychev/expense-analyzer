package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ResourceAlreadyExistsException extends CustomResponseStatusException {
    public ResourceAlreadyExistsException(String message) {
        this(message, null);
    }

    public ResourceAlreadyExistsException(String message, String field) {
        super(HttpStatus.CONFLICT, ExceptionCode.ALREADY_EXISTS.getValue(), ExceptionCode.ALREADY_EXISTS.getReason(), message, field);
    }
}
