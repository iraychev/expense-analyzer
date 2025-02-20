package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExceptionCode {

    RESOURCE_NOT_FOUND("ERR001", "Resource not found."),
    NULL_REQUEST_FIELDS("ERR002", "Request contains missing fields"),
    BANK_INTEGRATION_ERROR("ERR003", "Error while creating bank integration."),
    ALREADY_EXISTS("ERR004", "Resource already exists.");

    private final String value;
    private final String reason;
}
