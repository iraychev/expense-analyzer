package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BankIntegrationException extends CustomResponseStatusException {
    public BankIntegrationException(String message) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public BankIntegrationException(HttpStatus status, String message) {
        super(status, ExceptionCode.BANK_INTEGRATION_ERROR.getValue(), ExceptionCode.BANK_INTEGRATION_ERROR.getReason(), message);
    }
}
