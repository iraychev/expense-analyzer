package com.iraychev.expenseanalyzer.exception;

import lombok.experimental.Accessors;

@Accessors(chain = true)
public record ExceptionBody(String errorCode, String reason, String message) {
}
