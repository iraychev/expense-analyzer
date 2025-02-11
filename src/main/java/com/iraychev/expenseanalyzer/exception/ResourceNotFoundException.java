package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;

@Getter
public class ResourceNotFoundException extends CustomResponseStatusException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ExceptionCode.RESOURCE_NOT_FOUND.getValue(), ExceptionCode.RESOURCE_NOT_FOUND.getReason(), message);
    }
}