package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ValidationException extends CustomResponseStatusException {
    public ValidationException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, ExceptionCode.VALIDATION_ERROR.getValue(), ExceptionCode.VALIDATION_ERROR.getReason(), message, field);
    }
}
