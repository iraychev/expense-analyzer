package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExceptionCode {

    RESOURCE_NOT_FOUND("Resource not found."),
    NULL_REQUEST_FIELDS("Request contains missing fields"),
    BANK_INTEGRATION_ERROR("Error while creating bank integration."),
    ALREADY_EXISTS("Resource already exists.");

    private final String reason;
}
