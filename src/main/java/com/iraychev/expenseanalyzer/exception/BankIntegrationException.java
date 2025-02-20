package com.iraychev.expenseanalyzer.exception;

import org.springframework.http.HttpStatus;

public class BankIntegrationException extends CustomResponseStatusException {
    public BankIntegrationException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ExceptionCode.BANK_INTEGRATION_ERROR.getReason(), message);
    }
}
