package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class InvalidCredentialsException extends CustomResponseStatusException {
    public InvalidCredentialsException() {
        // 400 on purpose: clients treat any 401 as an expired session and log the user out.
        super(HttpStatus.BAD_REQUEST, ExceptionCode.INVALID_CREDENTIALS.getValue(), ExceptionCode.INVALID_CREDENTIALS.getReason(),
                "Incorrect username or password");
    }
}
