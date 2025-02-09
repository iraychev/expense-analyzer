package com.iraychev.expenseanalyzer.exception;

public class BankConnectionException extends RuntimeException {
    public BankConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
