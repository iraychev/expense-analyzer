package com.iraychev.expenseanalyzer.exception;

public class BankIntegrationException extends CustomResponseStatusException {
    public BankIntegrationException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ExceptionCode.BANK_INTEGRATION_ERROR.getValue(), ExceptionCode.BANK_INTEGRATION_ERROR.getReason(), message);
    }
}
