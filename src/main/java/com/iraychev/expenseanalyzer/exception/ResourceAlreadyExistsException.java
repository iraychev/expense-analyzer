package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ResourceAlreadyExistsException extends CustomResponseStatusException{
    public ResourceAlreadyExistsException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ExceptionCode.ALREADY_EXISTS.getValue(), ExceptionCode.ALREADY_EXISTS.getReason(), message);
    }
}
