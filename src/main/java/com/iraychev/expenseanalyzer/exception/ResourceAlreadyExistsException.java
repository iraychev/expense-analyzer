package com.iraychev.expenseanalyzer.exception;

import org.springframework.http.HttpStatus;

public class ResourceAlreadyExistsException extends CustomResponseStatusException{
    public ResourceAlreadyExistsException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ExceptionCode.ALREADY_EXISTS.getValue(), ExceptionCode.ALREADY_EXISTS.getReason(), message);

    }
}
