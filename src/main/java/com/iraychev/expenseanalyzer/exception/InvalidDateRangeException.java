package com.iraychev.expenseanalyzer.exception;

import lombok.Getter;

@Getter
public class InvalidDateRangeException extends RuntimeException {
    public InvalidDateRangeException(String message) {
        super(message);
    }
}
