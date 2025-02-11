package com.iraychev.expenseanalyzer.exception;

public class BankIntegrationException extends RuntimeException {
    public BankIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
