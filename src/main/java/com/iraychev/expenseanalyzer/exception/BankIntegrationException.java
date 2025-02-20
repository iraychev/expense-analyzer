package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BankIntegrationException extends CustomResponseStatusException {
    public BankIntegrationException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ExceptionCode.BANK_INTEGRATION_ERROR.getValue(), ExceptionCode.BANK_INTEGRATION_ERROR.getReason(), message);
    }
}
